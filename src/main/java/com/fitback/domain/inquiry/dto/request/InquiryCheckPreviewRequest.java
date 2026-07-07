package com.fitback.domain.inquiry.dto.request;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class InquiryCheckPreviewRequest {

    @Valid
    @NotNull(message = "고객 기본 정보는 필수입니다.")
    private CustomerInfo customer;

    @Valid
    @NotNull(message = "문의 정보는 필수입니다.")
    private InquiryInfo inquiry;

    @Getter
    @NoArgsConstructor
    public static class CustomerInfo {

        @NotBlank(message = "고객 이름은 필수입니다.")
        private String name;

        @NotNull(message = "성별은 필수입니다.")
        private Gender gender;

        @NotNull(message = "생년월일은 필수입니다.")
        private LocalDate birthDate;

        @NotBlank(message = "연락처는 필수입니다.")
        private String phoneNum;
    }

    @Getter
    @NoArgsConstructor
    public static class InquiryInfo {

        @NotNull(message = "문의 서비스는 필수입니다.")
        private UUID serviceId;

        @NotNull(message = "문의 상태는 필수입니다.")
        private InquiryStatus inquiryStatus;

        @NotBlank(message = "문의 내용은 필수입니다.")
        private String rawText;
    }
}
