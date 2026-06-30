package com.fitback.domain.consultation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ConsultationCreateResponse {

    private UUID consultationId;
    private UUID customerId;
    private Integer sessionNo;
    private String redirectUrl;
}
