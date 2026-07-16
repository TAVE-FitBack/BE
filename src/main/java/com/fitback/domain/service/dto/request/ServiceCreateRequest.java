package com.fitback.domain.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ServiceCreateRequest {

    @NotBlank(message = "서비스명을 입력하세요.")
    @Size(max = 100, message = "서비스명은 100자 이하여야 합니다.")
    private String name;

    private String description;

    private BigDecimal price;
}