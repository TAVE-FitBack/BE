package com.fitback.domain.inquiry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryStatus {

    RECEIVED("문의 접수", true),
    VISIT_SCHEDULED("방문 예정", true),
    VISIT_CANCELED("방문 취소", true),
    CONVERTED("상담 전환 완료", false);

    private final String label;
    private final boolean inputAllowed;
}
