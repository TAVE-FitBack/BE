package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.customer.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AiNextActionRegenerateRequest {

    private CustomerInfo customer;
    private LatestConsultationInfo latestConsultation;
    private AiAnalysisInfo aiAnalysis;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerInfo {
        private UUID customerId;
        private CustomerStatus status;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LatestConsultationInfo {
        private UUID consultationId;
        private String summary;
        private String rawText;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AiAnalysisInfo {
        private String leadTemperature;
        private String temperatureBasis;
        private List<NonConversionReasonInfo> nonConversionReasons;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NonConversionReasonInfo {
        private String reasonType;
        private String role;
        private String reasonBasis;
    }
}
