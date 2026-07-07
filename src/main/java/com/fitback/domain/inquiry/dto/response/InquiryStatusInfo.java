package com.fitback.domain.inquiry.dto.response;

import com.fitback.domain.inquiry.enums.InquiryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InquiryStatusInfo {

    private InquiryStatus status;
    private String label;

    public static InquiryStatusInfo from(InquiryStatus status) {
        return InquiryStatusInfo.builder()
                .status(status)
                .label(status.getLabel())
                .build();
    }
}
