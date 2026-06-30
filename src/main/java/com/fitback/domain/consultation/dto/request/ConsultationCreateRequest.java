package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.InflowPath;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ConsultationCreateRequest {

    private UUID customerId;

    @Valid
    @NotNull(message = "고객 정보는 필수입니다.")
    private CustomerInfo customer;

    @Valid
    @NotNull(message = "상담 정보는 필수입니다.")
    private ConsultationInfo consultation;

    @Getter
    @NoArgsConstructor
    public static class CustomerInfo {

        @NotBlank(message = "고객 이름은 필수입니다.")
        private String name;

        private Gender gender;

        private LocalDate birthDate;

        @NotBlank(message = "연락처는 필수입니다.")
        private String phoneNum;

        private PreferredContactChannel preferredContactChannel;

        private InflowPath inflowPath;
    }

    @Getter
    @NoArgsConstructor
    public static class ConsultationInfo {

        @NotNull(message = "상담 서비스는 필수입니다.")
        private UUID consultedServiceId;

        @NotNull(message = "방문/상담 일시는 필수입니다.")
        private OffsetDateTime consultedAt;

        @NotNull(message = "등록 여부는 필수입니다.")
        private Boolean isRegistered;

        @NotNull(message = "상담자는 필수입니다.")
        private UUID userId;

        @NotBlank(message = "상담 내용은 필수입니다.")
        private String rawText;
    }
}
