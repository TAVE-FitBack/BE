package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.store.enums.StoreType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AiConsultationAnalyzeRequest {

    private CustomerInfo customer;
    private ConsultationInfo consultation;
    private ServiceInfo service;
    private StoreContext storeContext;

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
        private UUID inflowPathId;
        private String inflowPathName;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ConsultationInfo {
        private UUID consultationId;
        private Integer sessionNo;
        private OffsetDateTime consultedAt;
        private UUID consultedServiceId;
        private ConsultationStage stage;
        private ConsultationSourceType sourceType;
        private String rawText;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ServiceInfo {
        private UUID serviceId;
        private String serviceName;
        private String description;
        private BigDecimal price;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class StoreContext {
        private UUID storeId;
        private StoreType storeType;
        private ConsultationRegistrationStatus registrationStatus;
    }
}
