package com.fitback.domain.customer.dto.response;

import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CustomerDetailResponse {

    private CustomerInfo customer;
    private LatestConsultation latestConsultation;
    private AiAnalysisStatus aiAnalysisStatus;
    private AiInsight aiInsight;
    private List<NonConversionReasonInfo> nonConversionReasons;
    private ActiveFollowUp activeFollowUp;
    private NextBestAction nextBestAction;
    private LatestMessageTemplate latestMessageTemplate;
    private List<TimelineItem> timeline;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerInfo {
        private UUID customerId;
        private String name;
        private Gender gender;
        private LocalDate birthDate;
        private String phoneNum;
        private PreferredContactChannel preferredContactChannel;
        private CustomerStatus status;
        private UUID registeredServiceId;
        private String registeredServiceName;
        private UUID inflowPathId;
        private String inflowPathName;
        private LocalDate firstConsultAt;
        private LocalDate latestConsultAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LatestConsultation {
        private UUID consultationId;
        private Integer sessionNo;
        private OffsetDateTime consultedAt;
        private UUID consultedServiceId;
        private String consultedServiceName;
        private UUID userId;
        private String counselorName;
        private ConsultationStage stage;
        private ConsultationSourceType sourceType;
        private String rawText;
        private String summary;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AiInsight {
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
        private String reasonType;
        private String role;
        private String reasonBasis;
        private String confidence;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ActiveFollowUp {
        private UUID followUpId;
        private UUID consultationId;
        private LocalDate recommendContactDate;
        private FollowUpStatus status;
        private String memo;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NextBestAction {
        private String title;
        private String description;
        private Map<String, Object> persuasionPoint;
        private String cautionNote;
        private Map<String, Object> actionBasis;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LatestMessageTemplate {
        private UUID messageTemplateId;
        private String content;
        private String tonePreset;
        private String versionType;
        private String deliveryStatus;
        private OffsetDateTime generatedAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class TimelineItem {
        private UUID timelineId;
        private CustomerActivityType activityType;
        private String title;
        private String description;
        private ActivityRelatedType relatedType;
        private UUID relatedId;
        private OffsetDateTime occurredAt;
    }
}
