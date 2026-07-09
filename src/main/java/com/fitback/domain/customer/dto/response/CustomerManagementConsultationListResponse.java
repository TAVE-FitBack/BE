package com.fitback.domain.customer.dto.response;

import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
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
        private ConsultationStage managementStage;
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
    public static class NonConversionReasonInfo {
        private String reasonType;
        private String displayName;
    }
}
