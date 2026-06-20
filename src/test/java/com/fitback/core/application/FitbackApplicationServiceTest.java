package com.fitback.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.fitback.core.infrastructure.AiTextAdapter;
import com.fitback.core.infrastructure.InMemoryTenantDataRepository;
import com.fitback.global.security.JwtTokenService;

class FitbackApplicationServiceTest {

    private FitbackApplicationService service;
    private InMemoryTenantDataRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTenantDataRepository();
        service = new FitbackApplicationService(repository, new AiTextAdapter("", "", true), new BCryptPasswordEncoder(),
                new JwtTokenService("local-development-secret-key-change-me", 86_400_000));
    }

    @Test
    void analysisCreatesDeterministicResultAndFollowUp() {
        Map<String, Object> customer = service.create("customers", Map.of("name", "Kim"));
        Map<String, Object> consultation = service.createConsultation(String.valueOf(customer.get("id")),
                Map.of("rawText", "서비스가 좋아요"));

        Map<String, Object> analyzed = service.analyzeConsultation(String.valueOf(consultation.get("id")));

        assertThat(analyzed).containsEntry("temperature", "HOT").containsEntry("provider", "LOCAL_FALLBACK");
        assertThat(service.get("customers", String.valueOf(customer.get("id")))).containsEntry("leadTemperature", "HOT");
        assertThat(service.by("followUps", "customerId", String.valueOf(customer.get("id")))).hasSize(1);
    }

    @Test
    void previewIsStatelessAndFinalSaveCreatesInsightGraph() {
        Map<String, Object> preview = service.analyzeConsultationPreview(
                Map.of("rawText", "바로 등록하고 싶고 사우나 가능 시간을 알고 싶어요"));
        assertThat(preview).containsEntry("saved", false).containsEntry("stateless", true);
        assertThat(repository.count("customers")).isZero();

        Map<String, Object> saved = service.createConsultationRecord(Map.of(
                "name", "Bae",
                "phoneNum", "010-7777-8888",
                "rawText", "바로 등록하고 싶고 사우나 가능 시간을 알고 싶어요",
                "serviceIds", List.of("day-pass")));

        String customerId = String.valueOf(saved.get("customerId"));
        assertThat(service.customerDetail(customerId)).containsEntry("leadTemperature", "HOT");
        assertThat(service.by("signals", "consultationId", String.valueOf(saved.get("consultationId")))).hasSize(2);
        assertThat(service.customersByIds(customerId).toString()).contains("HOT");
    }

    @Test
    void eventTargetsCustomersInterestedInItsService() {
        Map<String, Object> customer = service.create("customers", Map.of("name", "Lee"));
        service.replaceInterests(String.valueOf(customer.get("id")), Map.of("serviceIds", List.of("pt-10")));

        Map<String, Object> event = service.createEvent(Map.of("serviceId", "pt-10", "title", "Discount"));

        assertThat(event).containsEntry("targetCount", 1);
        assertThat(service.by("eventTargets", "eventId", String.valueOf(event.get("id")))).hasSize(1);
    }

    @Test
    void enrollmentMarksCustomerRegistered() {
        Map<String, Object> customer = service.create("customers", Map.of("name", "Park"));

        service.enroll(String.valueOf(customer.get("id")), Map.of("serviceId", "pt-10"));

        assertThat(service.get("customers", String.valueOf(customer.get("id")))).containsEntry("status", "REGISTERED");
    }

    @Test
    void interestReplacementRemovesPreviousValues() {
        Map<String, Object> customer = service.create("customers", Map.of("name", "Choi"));
        String customerId = String.valueOf(customer.get("id"));
        service.replaceInterests(customerId, Map.of("serviceIds", List.of("old")));

        service.replaceInterests(customerId, Map.of("serviceIds", List.of("new")));

        assertThat(service.interests(customerId)).extracting(item -> item.get("serviceId")).containsExactly("new");
    }

    @Test
    void reasonsRequireOnePrimaryAndAtMostTwoSubEntries() {
        assertThatThrownBy(() -> service.replaceReasons("consultation", Map.of("reasons",
                List.of(Map.of("reasonRole", "SUB1"), Map.of("reasonRole", "SUB1"), Map.of("reasonRole", "SUB2")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registeredContactResultUpdatesCustomerStatus() {
        Map<String, Object> customer = service.create("customers", Map.of("name", "Jung"));
        String customerId = String.valueOf(customer.get("id"));

        Map<String, Object> result = service.createContactResult(customerId, Map.of("status", "REGISTERED"));

        assertThat(result).containsEntry("customerStatusUpdated", true);
        assertThat(service.get("customers", customerId)).containsEntry("status", "REGISTERED");
        assertThat(service.by("enrollments", "customerId", customerId)).hasSize(1);
    }

    @Test
    void configuredAiFailureIsExplicit() {
        AiTextAdapter failing = new AiTextAdapter("key", "http://127.0.0.1:1", false);

        assertThatThrownBy(() -> failing.analyze("text"))
                .isInstanceOf(AiTextAdapter.AiProviderException.class);
    }

    @Test
    void customerSearchMasksPhoneAndSoftDeleteRemovesCustomer() {
        Map<String, Object> customer = service.create("customers",
                Map.of("name", "Search Kim", "phoneNum", "010-1234-5678", "status", "LEAD"));

        Map<String, Object> result = service.searchCustomers("LEAD", null, null, "Search", "name", "asc", 0, 20);
        assertThat((List<?>) result.get("content")).hasSize(1);
        assertThat(result.toString()).contains("***-****-5678");

        service.deleteCustomer(String.valueOf(customer.get("id")));
        assertThat(service.searchCustomers(null, null, null, null, null, "desc", 0, 20).get("content").toString())
                .doesNotContain("Search Kim");
    }

    @Test
    void messageSendAndSignedCallbackUseProviderMessageId() {
        Map<String, Object> message = service.create("messages", Map.of("content", "hello"));
        Map<String, Object> sending = service.sendMessage(String.valueOf(message.get("id")), Map.of("provider", "NAVER"));
        String providerMessageId = String.valueOf(sending.get("providerMessageId"));

        DeliveryCallbackService callbacks = new DeliveryCallbackService(repository, "secret");
        Map<String, Object> delivered = callbacks.apply("secret",
                Map.of("messageId", providerMessageId, "deliveryStatus", "DELIVERED"));

        assertThat(delivered).containsEntry("deliveryStatus", "DELIVERED");
    }
}
