package com.fitback.core.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitback.core.application.port.TenantDataRepository;
import com.fitback.core.application.port.AiAnalysisPort;
import com.fitback.global.security.JwtTokenService;
import com.fitback.global.security.InvalidRefreshTokenException;

@Service
public class FitbackApplicationService {

    private final TenantDataRepository store;
    private final AiAnalysisPort ai;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;

    public FitbackApplicationService(TenantDataRepository store, AiAnalysisPort ai, PasswordEncoder passwordEncoder,
            JwtTokenService tokens) {
        this.store = store;
        this.ai = ai;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    public Map<String, Object> register(Map<String, Object> body) {
        String email = required(body, "email");
        if (!store.matching("users", "email", email).isEmpty()) {
            throw new IllegalArgumentException("email already registered");
        }
        Map<String, Object> user = new LinkedHashMap<>(body);
        user.put("password", passwordEncoder.encode(required(body, "password")));
        user.putIfAbsent("role", "OWNER");
        user.put("storeId", UUID.randomUUID().toString());
        Map<String, Object> created = store.create("users", user);
        return Map.of("userId", created.get("id"), "message", "registration completed");
    }

    public Map<String, Object> login(Map<String, Object> body) {
        String email = required(body, "email");
        Map<String, Object> user = store.matching("users", "email", email).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!passwordEncoder.matches(required(body, "password"), String.valueOf(user.get("password")))) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return tokens(email, String.valueOf(user.get("storeId")));
    }

    public Map<String, Object> refresh(String refreshToken) {
        Map<String, Object> session = store.matching("refreshTokens", "token", refreshToken).stream().findFirst()
                .orElseThrow(InvalidRefreshTokenException::new);
        if (Instant.parse(String.valueOf(session.get("expiredAt"))).isBefore(Instant.now())) {
            store.removeMatching("refreshTokens", "token", refreshToken);
            throw new InvalidRefreshTokenException();
        }
        store.removeMatching("refreshTokens", "token", refreshToken);
        return tokens(String.valueOf(session.get("subject")), String.valueOf(session.get("storeId")));
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            store.removeMatching("refreshTokens", "token", refreshToken);
        }
    }

    public Map<String, Object> requestPasswordReset(String email) {
        store.matching("users", "email", email).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown email"));
        String token = "reset-" + UUID.randomUUID();
        store.create("passwordResetTokens", Map.of("token", token, "email", email,
                "expiredAt", Instant.now().plusSeconds(900).toString()));
        return Map.of("message", "password reset requested", "token", token);
    }

    public Map<String, Object> confirmPasswordReset(String token, String newPassword) {
        Map<String, Object> reset = store.matching("passwordResetTokens", "token", token).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid reset token"));
        if (Instant.parse(String.valueOf(reset.get("expiredAt"))).isBefore(Instant.now())) {
            throw new IllegalArgumentException("expired reset token");
        }
        Map<String, Object> user = store.matching("users", "email", reset.get("email")).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown email"));
        store.update("users", String.valueOf(user.get("id")), Map.of("password", passwordEncoder.encode(newPassword)));
        store.removeMatching("passwordResetTokens", "token", token);
        return Map.of("message", "password reset completed");
    }

    private Map<String, Object> tokens(String subject, String storeId) {
        String refreshToken = "refresh-" + UUID.randomUUID();
        store.create("refreshTokens", Map.of("token", refreshToken, "subject", subject, "storeId", storeId,
                "expiredAt", Instant.now().plusSeconds(2_592_000).toString()));
        return Map.of("accessToken", tokens.issue(subject, storeId), "refreshToken", refreshToken,
                "user", Map.of("email", subject, "storeId", storeId));
    }

    public Map<String, Object> singleton(String name) {
        return store.singleton(name);
    }

    public Map<String, Object> saveSingleton(String name, Map<String, Object> body) {
        return store.singleton(name, body);
    }

    public Map<String, Object> create(String collection, Map<String, Object> body) {
        return store.create(collection, body);
    }

    public List<Map<String, Object>> list(String collection) {
        return store.list(collection);
    }

    public Map<String, Object> get(String collection, String id) {
        return store.get(collection, id);
    }

    public Map<String, Object> customerDetail(String id) {
        return enrichCustomer(store.get("customers", id));
    }

    public Map<String, Object> update(String collection, String id, Map<String, Object> body) {
        return store.update(collection, id, body);
    }

    public void deleteCustomer(String id) {
        store.softDelete("customers", id);
    }

    public List<Map<String, Object>> by(String collection, String key, String value) {
        return store.matching(collection, key, value);
    }

    public Map<String, Object> createConsultation(String customerId, Map<String, Object> body) {
        store.get("customers", customerId);
        Map<String, Object> data = new LinkedHashMap<>(body);
        data.put("customerId", customerId);
        data.putIfAbsent("status", "PENDING_ANALYSIS");
        data.putIfAbsent("analysisStatus", "PENDING");
        Map<String, Object> consultation = store.create("consultations", data);
        Map<String, Object> followUp = new LinkedHashMap<>();
        followUp.put("customerId", customerId);
        followUp.put("consultationId", consultation.get("id"));
        followUp.put("status", "PENDING");
        store.create("followUps", followUp);
        return consultation;
    }

    public Map<String, Object> checkConsultationDuplicate(String phoneNum, String name) {
        List<Map<String, Object>> matches = phoneNum == null || phoneNum.isBlank()
                ? List.of()
                : store.matching("customers", "phoneNum", phoneNum);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isDuplicate", !matches.isEmpty());
        result.put("canProceed", matches.isEmpty());
        result.put("message", matches.isEmpty() ? "신규 상담 등록을 진행할 수 있습니다."
                : "이미 등록된 고객입니다. 기존 고객 재상담 흐름을 사용하세요.");
        result.put("phoneNum", phoneNum);
        result.put("name", name);
        if (!matches.isEmpty()) {
            result.put("customer", maskCustomerListItem(matches.get(0)));
        }
        return result;
    }

    public Map<String, Object> analyzeConsultationPreview(Map<String, Object> body) {
        String rawText = String.valueOf(body.getOrDefault("rawText", body.getOrDefault("quickMemo", "")));
        Map<String, Object> analysis = new LinkedHashMap<>(ai.analyze(rawText));
        analysis.put("stateless", true);
        analysis.put("saved", false);
        analysis.put("analyzedAt", Instant.now().toString());
        return analysis;
    }

    @Transactional
    public Map<String, Object> createConsultationRecord(Map<String, Object> body) {
        String phoneNum = String.valueOf(body.getOrDefault("phoneNum", body.getOrDefault("phoneNumber", "")));
        if (!phoneNum.isBlank() && !store.matching("customers", "phoneNum", phoneNum).isEmpty()) {
            throw new IllegalArgumentException("duplicate customer phone number");
        }

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", body.getOrDefault("name", body.getOrDefault("customerName", "이름 미상")));
        customer.put("phoneNum", phoneNum);
        customer.put("inflowPath", body.getOrDefault("inflowPath", body.getOrDefault("route", "OTHER")));
        customer.put("status", body.getOrDefault("status", "UNREGISTERED"));
        customer.put("leadTemperature", null);
        customer.put("analysisStatus", "PENDING");
        customer.put("analysisRequestedAt", Instant.now().toString());
        Map<String, Object> createdCustomer = store.create("customers", customer);

        List<String> serviceIds = normalizeServiceIds(body.get("serviceIds"), body.get("serviceId"));
        for (String serviceId : serviceIds) {
            store.create("interests", Map.of("customerId", createdCustomer.get("id"), "serviceId", serviceId));
        }

        String rawText = String.valueOf(body.getOrDefault("rawText", body.getOrDefault("quickMemo", "")));
        Map<String, Object> aiResult = body.get("aiResult") instanceof Map<?, ?> map ? cast(map) : ai.analyze(rawText);

        Map<String, Object> consultation = new LinkedHashMap<>();
        consultation.put("customerId", createdCustomer.get("id"));
        consultation.put("rawText", rawText);
        consultation.put("summary", aiResult.getOrDefault("summary", rawText));
        consultation.put("visitPurpose", body.getOrDefault("visitPurpose", body.get("purpose")));
        consultation.put("sourceType", "DIRECT");
        consultation.put("stage", "SAVED");
        consultation.put("status", "ANALYZED");
        consultation.put("analysisStatus", "COMPLETED");
        consultation.put("aiParsedAt", Instant.now().toString());
        Map<String, Object> createdConsultation = store.create("consultations", consultation);

        persistSignals(createdConsultation, aiResult);
        persistCustomerInsight(createdCustomer, createdConsultation, aiResult);

        Map<String, Object> followUp = new LinkedHashMap<>();
        followUp.put("customerId", createdCustomer.get("id"));
        followUp.put("consultationId", createdConsultation.get("id"));
        followUp.put("status", "PENDING");
        followUp.put("recommendContactDate", aiResult.getOrDefault("recommendContactDate", java.time.LocalDate.now().plusDays(1).toString()));
        store.create("followUps", followUp);

        return Map.of(
                "customerId", createdCustomer.get("id"),
                "consultationId", createdConsultation.get("id"),
                "analysisStatus", "COMPLETED",
                "customer", enrichCustomer(store.get("customers", String.valueOf(createdCustomer.get("id")))),
                "consultation", createdConsultation);
    }

    public Map<String, Object> analyzeConsultation(String consultationId) {
        Map<String, Object> consultation = store.get("consultations", consultationId);
        Map<String, Object> analysis = ai.analyze(String.valueOf(consultation.getOrDefault("rawText", "")));
        Map<String, Object> changes = new LinkedHashMap<>(analysis);
        changes.remove("reasons");
        changes.remove("signals");
        changes.put("aiParsedAt", Instant.now().toString());
        changes.put("status", "ANALYZED");
        changes.put("analysisStatus", "COMPLETED");
        Map<String, Object> analyzed = store.update("consultations", consultationId, changes);
        String customerId = String.valueOf(consultation.get("customerId"));
        if (analysis.containsKey("temperature")) {
            store.update("customers", customerId, Map.of(
                    "leadTemperature", analysis.get("temperature"),
                    "analysisStatus", "COMPLETED",
                    "analyzedAt", Instant.now().toString()));
        }
        persistSignals(consultation, analysis);
        persistCustomerInsight(store.get("customers", customerId), consultation, analysis);
        if (analysis.get("reasons") instanceof List<?> reasons && !reasons.isEmpty()) {
            replaceReasons(consultationId, Map.of("reasons", reasons));
        }
        store.matching("followUps", "consultationId", consultationId).stream().findFirst().ifPresent(followUp -> {
            Map<String, Object> followUpChanges = new LinkedHashMap<>();
            copyIfPresent(analysis, followUpChanges, "recommendContactDate");
            copyIfPresent(analysis, followUpChanges, "persuasionPoints");
            copyIfPresent(analysis, followUpChanges, "temperatureBasis");
            if (!followUpChanges.isEmpty()) {
                store.update("followUps", String.valueOf(followUp.get("id")), followUpChanges);
            }
        });
        return analyzed;
    }

    @Transactional
    public List<Map<String, Object>> replaceInterests(String customerId, Map<String, Object> body) {
        List<?> ids = body.get("serviceIds") instanceof List<?> values ? values : List.of();
        store.removeMatching("interests", "customerId", customerId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object serviceId : ids) {
            result.add(store.create("interests", Map.of("customerId", customerId, "serviceId", String.valueOf(serviceId))));
        }
        return result;
    }

    public List<Map<String, Object>> interests(String customerId) {
        return store.matching("interests", "customerId", customerId);
    }

    public List<Map<String, Object>> replaceReasons(String consultationId, Map<String, Object> body) {
        List<?> reasons = body.get("reasons") instanceof List<?> values ? values : List.of(body);
        long primaryCount = reasons.stream().filter(reason -> role(reason).equals("PRIMARY")).count();
        long subCount = reasons.stream().filter(reason -> role(reason).equals("SUB1") || role(reason).equals("SUB2")).count();
        long invalidCount = reasons.stream().filter(reason -> !List.of("PRIMARY", "SUB1", "SUB2").contains(role(reason))).count();
        long distinctRoleCount = reasons.stream().map(this::role).distinct().count();
        if (primaryCount != 1 || subCount > 2 || invalidCount > 0 || distinctRoleCount != reasons.size()) {
            throw new IllegalArgumentException("reasons require PRIMARY and optional unique SUB1/SUB2 entries");
        }
        store.removeMatching("reasons", "consultationId", consultationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object reason : reasons) {
            Map<String, Object> value = reason instanceof Map<?, ?> map ? cast(map) : Map.of("reason", reason);
            Map<String, Object> data = new LinkedHashMap<>(value);
            data.put("consultationId", consultationId);
            result.add(store.create("reasons", data));
        }
        return result;
    }

    public Map<String, Object> createContactResult(String customerId, Map<String, Object> body) {
        Map<String, Object> data = new LinkedHashMap<>(body);
        data.put("customerId", customerId);
        boolean registered = "REGISTERED".equals(String.valueOf(body.get("status")));
        data.put("customerStatusUpdated", registered);
        if (registered) {
            store.update("customers", customerId, Map.of("status", "REGISTERED"));
            Map<String, Object> enrollment = new LinkedHashMap<>();
            enrollment.put("customerId", customerId);
            enrollment.put("serviceId", body.getOrDefault("serviceId", "UNSPECIFIED"));
            enrollment.put("status", "ACTIVE");
            store.create("enrollments", enrollment);
        }
        return store.create("contactResults", data);
    }

    public Map<String, Object> searchCustomers(String status, String temperature, String reasonType, String search,
            String sortBy, String order, int page, int size) {
        List<Map<String, Object>> filtered = store.list("customers").stream()
                .filter(customer -> status == null || status.equals(String.valueOf(customer.get("status"))))
                .filter(customer -> temperature == null || temperature.equals(String.valueOf(customer.get("leadTemperature"))))
                .filter(customer -> search == null || String.valueOf(customer.getOrDefault("name", "")).contains(search)
                        || String.valueOf(customer.getOrDefault("phoneNum", "")).contains(search))
                .filter(customer -> reasonType == null || reasonType.equals(String.valueOf(customer.get("primaryReason"))))
                .sorted((left, right) -> {
                    String key = sortBy == null ? "createdAt" : sortBy;
                    int compared = String.valueOf(left.getOrDefault(key, "")).compareTo(String.valueOf(right.getOrDefault(key, "")));
                    return "asc".equalsIgnoreCase(order) ? compared : -compared;
                })
                .map(this::maskCustomerListItem)
                .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return Map.of("content", filtered.subList(from, to), "totalElements", filtered.size(),
                "totalPages", filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size);
    }

    public Map<String, Object> customersByIds(String ids) {
        List<String> requested = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .toList();
        List<Map<String, Object>> customers = requested.stream()
                .map(id -> {
                    try {
                        return maskCustomerListItem(enrichCustomer(store.get("customers", id)));
                    } catch (RuntimeException ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return Map.of("content", customers, "totalElements", customers.size(), "polling", true);
    }

    public List<Map<String, Object>> generateMessages(String followUpId) {
        Map<String, Object> followUp = store.get("followUps", followUpId);
        String customerId = String.valueOf(followUp.get("customerId"));
        String name = String.valueOf(store.get("customers", customerId).get("name"));
        return ai.generateMessages(name).stream().map(message -> {
            Map<String, Object> data = new LinkedHashMap<>(message);
            data.put("followUpId", followUpId);
            data.put("deliveryStatus", "DRAFT");
            return store.create("messages", data);
        }).toList();
    }

    public Map<String, Object> sendMessage(String messageId, Map<String, Object> body) {
        String provider = String.valueOf(body == null ? "LOCAL" : body.getOrDefault("provider", "LOCAL"));
        String providerMessageId = provider.toLowerCase() + "-" + UUID.randomUUID();
        return store.update("messages", messageId, Map.of("provider", provider, "providerMessageId", providerMessageId,
                "deliveryStatus", "SENDING", "sentAt", Instant.now().toString()));
    }

    public Map<String, Object> createEvent(Map<String, Object> body) {
        Map<String, Object> event = store.create("events", body);
        String serviceId = String.valueOf(body.get("serviceId"));
        int targets = 0;
        for (Map<String, Object> interest : store.matching("interests", "serviceId", serviceId)) {
            store.create("eventTargets", Map.of("eventId", event.get("id"), "customerId", interest.get("customerId"),
                    "status", "PENDING"));
            targets++;
        }
        return store.update("events", String.valueOf(event.get("id")), Map.of("targetCount", targets));
    }

    public Map<String, Object> enroll(String customerId, Map<String, Object> body) {
        Map<String, Object> data = new LinkedHashMap<>(body);
        data.put("customerId", customerId);
        data.putIfAbsent("status", "ACTIVE");
        Map<String, Object> enrollment = store.create("enrollments", data);
        store.update("customers", customerId, Map.of("status", "REGISTERED", "customerStatusUpdated", true));
        return enrollment;
    }

    public Map<String, Object> summary() {
        return Map.of("todayConsultCount", store.count("consultations"),
                "monthlyRegistrationRate", rate(store.count("enrollments"), store.count("consultations")),
                "pendingFollowUpCount", store.matching("followUps", "status", "PENDING").size(),
                "overdueFollowUpCount", 0);
    }

    public List<Map<String, Object>> priorityCustomers() {
        return store.list("customers").stream().limit(5).map(customer -> {
            Map<String, Object> result = new LinkedHashMap<>(customer);
            result.put("priorityScore", 50);
            return result;
        }).toList();
    }

    public Object report(String type) {
        return switch (type) {
            case "conversion" -> Map.of(
                    "monthly", List.of(Map.of("month", "current", "registeredCount", store.count("enrollments"),
                            "consultCount", store.count("consultations"),
                            "registrationRate", rate(store.count("enrollments"), store.count("consultations")))),
                    "prevPeriodRate", 0, "change", 0);
            case "non-conversion-reasons" -> store.list("reasons");
            case "follow-up-funnel" -> Map.of("stages", List.of(Map.of("stage", "PENDING", "count",
                    store.matching("followUps", "status", "PENDING").size())));
            default -> Map.of("inflowPaths", List.of(), "interestServices", store.list("interests"));
        };
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(value);
    }

    private String role(Object reason) {
        if (reason instanceof Map<?, ?> map) {
            Object role = map.containsKey("reasonRole") ? map.get("reasonRole") : map.get("role");
            return String.valueOf(role);
        }
        return "";
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Map<String, Object> maskCustomerListItem(Map<String, Object> customer) {
        Map<String, Object> masked = new LinkedHashMap<>(customer);
        String phone = String.valueOf(masked.getOrDefault("phoneNum", ""));
        if (phone.length() >= 4) {
            masked.put("phoneNum", "***-****-" + phone.substring(phone.length() - 4));
        }
        return masked;
    }

    private Map<String, Object> enrichCustomer(Map<String, Object> customer) {
        Map<String, Object> result = new LinkedHashMap<>(customer);
        String customerId = String.valueOf(customer.get("id"));
        store.matching("customerInsights", "customerId", customerId).stream().findFirst()
                .ifPresent(insight -> result.put("aiInsight", insight));
        List<Map<String, Object>> reasons = store.matching("reasons", "customerId", customerId);
        if (!reasons.isEmpty()) {
            result.put("nonConversionReasons", reasons);
            result.put("primaryReason", reasons.get(0).get("reasonType"));
        }
        List<Map<String, Object>> consultations = store.matching("consultations", "customerId", customerId);
        result.put("consultations", consultations);
        if (!consultations.isEmpty()) {
            String latestConsultationId = String.valueOf(consultations.get(consultations.size() - 1).get("id"));
            result.put("signals", store.matching("signals", "consultationId", latestConsultationId));
        }
        return result;
    }

    private void persistSignals(Map<String, Object> consultation, Map<String, Object> analysis) {
        Object value = analysis.get("signals");
        if (!(value instanceof List<?> signals)) {
            return;
        }
        String consultationId = String.valueOf(consultation.get("id"));
        store.removeMatching("signals", "consultationId", consultationId);
        for (Object signal : signals) {
            Map<String, Object> signalMap = signal instanceof Map<?, ?> map ? new LinkedHashMap<>(cast(map))
                    : new LinkedHashMap<>(Map.of("signalType", "NOTE", "signalValue", String.valueOf(signal)));
            signalMap.put("consultationId", consultationId);
            store.create("signals", signalMap);
        }
    }

    private void persistCustomerInsight(Map<String, Object> customer, Map<String, Object> consultation, Map<String, Object> analysis) {
        String customerId = String.valueOf(customer.get("id"));
        store.removeMatching("customerInsights", "customerId", customerId);
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("customerId", customerId);
        insight.put("consultationId", consultation.get("id"));
        insight.put("leadTemperature", analysis.getOrDefault("leadTemperature", analysis.get("temperature")));
        insight.put("temperatureBasis", analysis.get("temperatureBasis"));
        insight.put("priorityScore", "HOT".equals(String.valueOf(insight.get("leadTemperature"))) ? 90 : 65);
        insight.put("nextBestAction", analysis.get("nextBestAction"));
        insight.put("analysisStatus", "COMPLETED");
        insight.put("analyzedAt", Instant.now().toString());
        store.create("customerInsights", insight);
        Map<String, Object> customerChanges = new LinkedHashMap<>();
        customerChanges.put("leadTemperature", insight.get("leadTemperature"));
        customerChanges.put("analysisStatus", "COMPLETED");
        customerChanges.put("priorityScore", insight.get("priorityScore"));
        customerChanges.put("nextBestAction", insight.get("nextBestAction"));
        store.update("customers", customerId, customerChanges);

        if (analysis.get("reasons") instanceof List<?> reasons && !reasons.isEmpty()) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object reason : reasons) {
                Map<String, Object> reasonMap = reason instanceof Map<?, ?> map ? new LinkedHashMap<>(cast(map))
                        : new LinkedHashMap<>(Map.of("reasonType", String.valueOf(reason), "reasonRole", "PRIMARY"));
                reasonMap.put("customerId", customerId);
                reasonMap.put("consultationId", consultation.get("id"));
                normalized.add(reasonMap);
            }
            replaceReasons(String.valueOf(consultation.get("id")), Map.of("reasons", normalized));
        }
    }

    private List<String> normalizeServiceIds(Object serviceIds, Object serviceId) {
        if (serviceIds instanceof List<?> values) {
            return values.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
        }
        if (serviceId != null && !String.valueOf(serviceId).isBlank()) {
            return List.of(String.valueOf(serviceId));
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }
}
