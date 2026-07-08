package com.fitback.domain.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CustomerAiAnalysisUpdateResponse {

    private UUID customerId;
    private boolean nextActionRegenerationAvailable;
}
