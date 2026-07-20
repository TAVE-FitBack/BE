package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CustomerInfoUpdateResponse {

    private UUID customerId;
    private String name;
    private LocalDate birthDate;
    private Gender gender;
    private String phoneNum;
    private UUID consultationId;
    private OffsetDateTime visitAt;
}
