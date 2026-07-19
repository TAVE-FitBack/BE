package com.fitback.domain.customer.dto.request;

import com.fitback.domain.customer.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CustomerStatusUpdateRequest {

    @NotNull(message = "고객 상태는 필수입니다.")
    @Schema(
            description = "Customer status to change to",
            allowableValues = {"REGISTERED", "PENDING", "SCHEDULED", "LOST", "NO_SHOW"},
            example = "REGISTERED"
    )
    private CustomerStatus status;

    @Schema(description = "Registered service ID. Required when status is REGISTERED")
    private UUID registeredServiceId;
}
