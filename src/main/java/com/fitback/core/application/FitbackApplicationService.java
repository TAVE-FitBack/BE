package com.fitback.core.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitback.core.domain.FitbackStore;
import com.fitback.core.infrastructure.AiTextAdapter;

@Service
public class FitbackApplicationService {

    private final FitbackStore store;
    private final AiTextAdapter ai;
    private final PasswordEncoder passwordEncoder;

    public FitbackApplicationService(FitbackStore store, AiTextAdapter ai, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.ai = ai;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> register(Map<String, Object> body) {
        String email = required(body, "email");
        if (!store.matching("users", "email", email).isEmpty()) {
            throw new IllegalArgumentException("email already registered");
        }
        Map<String, Object> user = new LinkedHashMap<>(body);
        user.put("password", passwordEncoder.encode(required(body, "password")));
        user.putIfAbsent("role", "OWNER");
        store.create("users", user);
        return tokens(email);
    }

    public Map<String, Object> login(Map<String, Object> body) {
        String email = required(body, "email");
        Map<String, Object> user = store.matching("users", "email", email).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!passwordEncoder.matches(required(body, "password"), String.valueOf(user.get("password")))) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return tokens(email);
    }

    public Map<String, Object> tokens(String subject) {
        return Map.of("accessToken", "access-" + UUID.randomUUID(), "refreshToken", "refresh-" + UUID.randomUUID(),
                "user", Map.of("email", subject));
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
        Map<String, Object> consultation = store.create("consultations", data);
        Map<String, Object> followUp = new LinkedHashMap<>();
        followUp.put("customerId", customerId);
        followUp.put("consultationId", consultation.get("id"));
        followUp.put("status", "PENDING");
        store.create("followUps", followUp);
        return consultation;
    }

    public Map<String, Object> analyzeConsultation(String consultationId) {
        Map<String, Object> consultation = store.get("consultations", consultationId);
        Map<String, Object> analysis = ai.analyze(String.valueOf(consultation.getOrDefault("rawText", "")));
        Map<String, Object> changes = new LinkedHashMap<>(analysis);
        changes.put("aiParsedAt", Instant.now().toString());
        changes.put("status", "ANALYZED");
        return store.update("consultations", consultationId, changes);
    }

    @Transactional
    public List<Map<String, Object>> replaceInterests(String customerId, Map<String, Object> body) {
        List<?> ids = body.get("serviceIds") instanceof List<?> values ? values : List.of();
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
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object reason : reasons) {
            Map<String, Object> value = reason instanceof Map<?, ?> map ? cast(map) : Map.of("reason", reason);
            Map<String, Object> data = new LinkedHashMap<>(value);
            data.put("consultationId", consultationId);
            result.add(store.create("reasons", data));
        }
        return result;
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
        return Map.of("todayConsultCount", store.count("consultations"), "registeredCount", store.count("enrollments"),
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

    public Map<String, Object> report(String type) {
        return switch (type) {
            case "conversion" -> Map.of("registeredCount", store.count("enrollments"), "consultCount",
                    store.count("consultations"), "registrationRate", rate(store.count("enrollments"), store.count("consultations")));
            case "non-conversion-reasons" -> Map.of("reasons", store.list("reasons"));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }
}
