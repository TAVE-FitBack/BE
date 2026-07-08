package com.fitback.domain.customer.dto.response;

import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ReconsultationCreateResponse {

    private UUID customerId;
    private UUID consultationId;
    private Integer sessionNo;
    private AiAnalysisStatus aiAnalysisStatus;
}
