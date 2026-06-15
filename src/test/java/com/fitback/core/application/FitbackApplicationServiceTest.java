package com.fitback.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.fitback.core.domain.FitbackStore;
import com.fitback.core.infrastructure.AiTextAdapter;
import com.fitback.global.security.JwtTokenService;

class FitbackApplicationServiceTest {

    private FitbackApplicationService service;

    @BeforeEach
    void setUp() {
        service = new FitbackApplicationService(new FitbackStore(), new AiTextAdapter(""), new BCryptPasswordEncoder(),
                new JwtTokenService("local-development-secret-key-change-me", 86_400_000));
    }

    @Test
    void analysisCreatesDeterministicResultAndFollowUp() {
        Map<String, Object> customer = service.create("customers", Map.of("name", "Kim"));
        Map<String, Object> consultation = service.createConsultation(String.valueOf(customer.get("id")),
                Map.of("rawText", "서비스가 좋아요"));

        Map<String, Object> analyzed = service.analyzeConsultation(String.valueOf(consultation.get("id")));

        assertThat(analyzed).containsEntry("temperature", "HOT").containsEntry("provider", "LOCAL_FALLBACK");
        assertThat(service.by("followUps", "customerId", String.valueOf(customer.get("id")))).hasSize(1);
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
}
