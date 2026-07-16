package com.fitback.domain.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InflowPathCreateRequest {

    @NotBlank(message = "유입 경로명을 입력하세요.")
    @Size(max = 100, message = "유입 경로명은 100자 이하여야 합니다.")
    private String name;

    private int displayOrder;

    private boolean active;
}