package com.fitback.domain.inquiry.dto.request;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class AiInquiryCheckPreviewRequest {

    private String rawText;
    private String serviceName;
    private InquiryStatus inquiryStatus;
    private CustomerInfo customerInfo;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerInfo {
        private String name;
        private Gender gender;
        private LocalDate birthDate;
    }
}
