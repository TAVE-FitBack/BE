package com.fitback.domain.customer.dto.response;

import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerManagementConsultationListResponse {

    private List<ConsultationItem> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;

    @Getter
    @Builder
    public static class ConsultationItem {
        private UUID customerId;
        private String name;
        private String phoneNum;
        private Gender gender;
        private LocalDate birthDate;
        private UUID serviceId;
        private String serviceName;
        private UUID inflowPathId;
        private String inflowPathName;
        @Schema(description = "기존 상담 관리단계", example = "CONSULTATION")
        private ConsultationStage managementStage;

        @Schema(description = "후속관리 관리단계")
        private FollowUpManagementStageResponse followUpManagementStage;
        private String latestMemo;
        private List<NonConversionReasonInfo> nonConversionReasons;
        private String leadTemperature;
        private CustomerStatus customerStatus;
        private OffsetDateTime latestConsultAt;
        private UUID counselorId;
        private String counselorName;
    }

    @Getter
    @Builder
    public static class FollowUpManagementStageResponse {
        @Schema(description = "후속관리 관리단계 타입", allowableValues = {"ROUND", "COMPLETED", "CLOSED", "NONE"})
        private FollowUpManagementStageType type;

        @Schema(description = "후속관리 연락 차수. type이 NONE이면 null", example = "2")
        private Integer contactRound;

        @Schema(description = "후속관리 관리단계 표시명", example = "2차 연락 대상")
        private String label;
    }

    public enum FollowUpManagementStageType {
        ROUND,
        COMPLETED,
        CLOSED,
        NONE
    }

    @Getter
    @Builder
    public static class NonConversionReasonInfo {
        private String reasonType;
        private String displayName;
    }
}
