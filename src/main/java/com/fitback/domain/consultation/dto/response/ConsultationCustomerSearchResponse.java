package com.fitback.domain.consultation.dto.response;

import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ConsultationCustomerSearchResponse {

    private boolean exists;
    private CustomerInfo customer;
    private String redirectUrl;

    public static ConsultationCustomerSearchResponse notFound() {
        return ConsultationCustomerSearchResponse.builder()
                .exists(false)
                .customer(null)
                .redirectUrl(null)
                .build();
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerInfo {
        private UUID customerId;
        private String name;
        private Gender gender;
        private LocalDate birthDate;
        private String phoneNum;
        private UUID registeredServiceId;
        private CustomerStatus status;
        private PreferredContactChannel preferredContactChannel;
        private UUID inflowPathId;
        private LocalDate latestConsultAt;
    }
}
