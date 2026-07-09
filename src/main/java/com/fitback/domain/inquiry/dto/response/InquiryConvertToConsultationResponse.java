package com.fitback.domain.inquiry.dto.response;

import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class InquiryConvertToConsultationResponse {

    private UUID customerId;
    private UUID consultationId;
    private Integer sessionNo;
    private UUID inquiryId;
    private InquiryStatus inquiryStatus;
    private AiAnalysisStatus aiAnalysisStatus;
    private String redirectUrl;
}
