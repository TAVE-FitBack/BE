package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.customer.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class AiCheckPreviewRequest {

    private String rawText;
    private String serviceName;
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
