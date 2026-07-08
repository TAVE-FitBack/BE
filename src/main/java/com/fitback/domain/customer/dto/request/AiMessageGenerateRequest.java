package com.fitback.domain.customer.dto.request;

import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.MessageTonePreset;
import com.fitback.domain.customer.enums.MessageVersionType;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AiMessageGenerateRequest {

    private CustomerInfo customer;
    private LatestConsultationInfo latestConsultation;
    private AiInsightInfo aiInsight;
    private List<NonConversionReasonInfo> nonConversionReasons;
    private NextBestActionInfo nextBestAction;
    private EventInfo event;
    private MessageOptions messageOptions;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerInfo {
        private UUID customerId;
        private String name;
        private PreferredContactChannel preferredContactChannel;
        private CustomerStatus status;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LatestConsultationInfo {
        private UUID consultationId;
        private String summary;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AiInsightInfo {
        private String leadTemperature;
        private Integer priorityScore;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NonConversionReasonInfo {
        private String reasonType;
        private String role;
        private String reasonBasis;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NextBestActionInfo {
        private String title;
        private String description;
        private Map<String, Object> persuasionPoint;
        private String cautionNote;
        private Map<String, Object> actionBasis;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class EventInfo {
        private UUID eventId;
        private String title;
        private String description;
        private BigDecimal discountRate;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MessageOptions {
        private MessageTonePreset tonePreset;
        private MessageVersionType versionType;
        private String additionalInstruction;
    }
}
