package com.fitback.domain.store.dto.response;

import com.fitback.domain.store.enums.StoreType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class StoreSetupResponse {

    private UUID storeId;
    private String name;
    private StoreType storeType;
}