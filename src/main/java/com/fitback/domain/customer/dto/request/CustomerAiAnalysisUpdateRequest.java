package com.fitback.domain.customer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CustomerAiAnalysisUpdateRequest {

    @NotBlank(message = "AI 상담요약은 필수입니다.")
    private String summary;

    @NotBlank(message = "고객온도는 필수입니다.")
    private String leadTemperature;

    @NotBlank(message = "고객온도 근거는 필수입니다.")
    private String temperatureBasis;

    @Valid
    @NotNull(message = "주요 이탈요인은 필수입니다.")
    private List<NonConversionReasonInfo> nonConversionReasons;

    @Getter
    @NoArgsConstructor
    public static class NonConversionReasonInfo {

        @NotBlank(message = "이탈요인 유형은 필수입니다.")
        private String reasonType;

        @NotBlank(message = "이탈요인 역할은 필수입니다.")
        private String role;

        @NotBlank(message = "이탈요인 근거는 필수입니다.")
        private String reasonBasis;

        @NotBlank(message = "이탈요인 신뢰도는 필수입니다.")
        private String confidence;
    }
}
