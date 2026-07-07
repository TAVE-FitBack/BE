package com.fitback.domain.inquiry.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class InquiryCreateResponse {

    private UUID inquiryId;
    private String redirectUrl;
}
