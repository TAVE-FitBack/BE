package com.fitback.domain.customer.dto.request;

import com.fitback.domain.customer.enums.CustomerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CustomerStatusUpdateRequest {

    @NotNull(message = "고객 상태는 필수입니다.")
    private CustomerStatus status;

    private UUID registeredServiceId;
}
