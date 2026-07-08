package com.fitback.domain.customer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ReconsultationCheckPreviewRequest {

    @Valid
    @NotNull(message = "상담 정보는 필수입니다.")
    private ConsultationInfo consultation;

    @Getter
    @NoArgsConstructor
    public static class ConsultationInfo {

        @NotNull(message = "상담 서비스는 필수입니다.")
        private UUID consultedServiceId;

        @NotBlank(message = "상담 내용은 필수입니다.")
        private String rawText;
    }
}
