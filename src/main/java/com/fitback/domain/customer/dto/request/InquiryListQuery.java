package com.fitback.domain.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class InquiryListQuery {

    @Schema(example = "2026-10", description = "조회 월(YYYY-MM). 미입력 시 현재 월")
    private String month;

    @Schema(description = "이름, 연락처 또는 상품명 검색")
    private String keyword;

    private String gender;
    private UUID serviceId;
    private UUID inflowPathId;
    private String inquiryStatus;
    private UUID counselorId;

    @Schema(defaultValue = "0", minimum = "0")
    private int page = 0;

    @Schema(defaultValue = "10", minimum = "1", maximum = "100")
    private int size = 10;
}
