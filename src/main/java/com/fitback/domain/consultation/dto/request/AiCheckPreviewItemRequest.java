package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiCheckPreviewItemRequest {

    @NotNull(message = "AI 중간분석 key는 필수입니다.")
    private AiCheckSignalKey key;

    @NotBlank(message = "AI 중간분석 표시명은 필수입니다.")
    private String label;

    @NotNull(message = "AI 중간분석 확인 여부는 필수입니다.")
    private Boolean confirmed;

    private String value;

    public int getDisplayOrder() {
        return key.getDisplayOrder();
    }
}
