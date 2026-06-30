package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.customer.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ConsultationCheckPreviewRequest {

    @Valid
    @NotNull(message = "고객 기본 정보는 필수입니다.")
    private CustomerInfo customer;

    @Valid
    @NotNull(message = "상담 정보는 필수입니다.")
    private ConsultationInfo consultation;

    @Getter
    @NoArgsConstructor
    public static class CustomerInfo {

        private UUID customerId;

        @NotBlank(message = "고객 이름은 필수입니다.")
        private String name;

        private Gender gender;

        private LocalDate birthDate;

        private String phoneNum;
    }

    @Getter
    @NoArgsConstructor
    public static class ConsultationInfo {

        @NotNull(message = "상담 서비스는 필수입니다.")
        private UUID consultedServiceId;

        @NotBlank(message = "상담 내용은 필수입니다.")
        private String rawText;
    }
}
