package com.fitback.domain.store.dto.response;

import com.fitback.domain.store.entity.InflowPathOption;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class InflowPathResponse {

    private UUID id;
    private String name;
    private int displayOrder;
    private boolean active;

    public static InflowPathResponse from(InflowPathOption option) {
        return InflowPathResponse.builder()
                .id(option.getId())
                .name(option.getName())
                .displayOrder(option.getDisplayOrder())
                .active(option.isActive())
                .build();
    }
}