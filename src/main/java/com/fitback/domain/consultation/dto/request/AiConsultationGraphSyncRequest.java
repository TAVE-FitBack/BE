package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.store.enums.StoreType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AiConsultationGraphSyncRequest {

    private StoreInfo store;
    private ServiceInfo service;
    private CustomerInfo customer;
    private ConsultationInfo consultation;
    private CustomerAiInsightInfo customerAiInsight;

    @Builder.Default
    private List<NonConversionReasonInfo> nonConversionReasons = List.of();

    private FollowUpInfo followUp;
    private FollowUpAiInsightInfo followUpAiInsight;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class StoreInfo {
        private UUID storeId;
        private StoreType storeType;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ServiceInfo {
        private UUID serviceId;
        private UUID storeId;
        private String serviceName;
        private String description;
        private BigDecimal price;
        private Boolean active;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerInfo {
        private UUID customerId;
        private UUID storeId;
        private UUID registeredServiceId;
        private String name;
        private Gender gender;
        private LocalDate birthDate;
        private String phoneNum;
        private PreferredContactChannel preferredContactChannel;
        private CustomerStatus status;
        private UUID inflowPathId;
        private String inflowPathName;
        private OffsetDateTime registeredAt;
        private LocalDate firstConsultAt;
        private LocalDate latestConsultAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ConsultationInfo {
        private UUID consultationId;
        private UUID customerId;
        private UUID consultedServiceId;
        private Integer sessionNo;
        private OffsetDateTime consultedAt;
        private ConsultationStage stage;
        private ConsultationSourceType sourceType;
        private String rawText;
        private String summary;
        private AiAnalysisStatus aiAnalysisStatus;
        private OffsetDateTime aiParsedAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerAiInsightInfo {
        private UUID customerId;
        private String leadTemperature;
        private String temperatureBasis;
        private Integer priorityScore;
        private OffsetDateTime analyzedAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NonConversionReasonInfo {
        private UUID reasonId;
        private UUID customerId;
        private UUID consultationId;
        private String reasonType;
        private String role;
        private String reasonBasis;
        private String confidence;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class FollowUpInfo {
        private UUID followUpId;
        private UUID customerId;
        private UUID consultationId;
        private LocalDate recommendContactDate;
        private FollowUpStatus status;
        private Integer contactRound;
        private Boolean hasReply;
        private OffsetDateTime repliedAt;
        private LocalDate snoozedUntil;
        private String memo;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class FollowUpAiInsightInfo {
        private UUID followUpId;
        private Map<String, Object> persuasionPoint;
        private String cautionNote;
        private Map<String, Object> actionBasis;
        private OffsetDateTime analyzedAt;
    }
}
