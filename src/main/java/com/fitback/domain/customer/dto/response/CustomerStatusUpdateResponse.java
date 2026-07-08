package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CustomerStatusUpdateResponse {

    private UUID customerId;
    private CustomerStatus status;
    private String followUpAction;
    private boolean nextActionRegenerationAvailable;
}
