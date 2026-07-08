package com.fitback.domain.customer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ReconsultationCreateRequest {

    @Valid
    @NotNull(message = "상담 정보는 필수입니다.")
    private ConsultationInfo consultation;

    @Getter
    @NoArgsConstructor
    public static class ConsultationInfo {

        @NotNull(message = "상담 서비스는 필수입니다.")
        private UUID consultedServiceId;

        @NotNull(message = "방문/상담 일시는 필수입니다.")
        private OffsetDateTime consultedAt;

        @NotNull(message = "상담자는 필수입니다.")
        private UUID userId;

        @NotBlank(message = "상담 내용은 필수입니다.")
        private String rawText;
    }
}
