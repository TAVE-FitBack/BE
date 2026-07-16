package com.fitback.domain.service.dto.response;

import com.fitback.domain.service.entity.Service;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ServiceResponse {

    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private boolean active;

    public static ServiceResponse from(Service service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .price(service.getPrice())
                .active(service.isActive())
                .build();
    }
}
