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

    @Schema(description = "성별", allowableValues = {"MALE", "FEMALE"})
    private String gender;

    @Schema(description = "문의 상품 ID")
    private UUID serviceId;

    @Schema(description = "문의 경로 ID")
    private UUID inflowPathId;

    @Schema(
            description = "문의 상태. 전환 완료 문의는 목록에서 제외",
            allowableValues = {"RECEIVED", "VISIT_SCHEDULED", "VISIT_CANCELED"}
    )
    private String inquiryStatus;

    @Schema(description = "담당자 ID")
    private UUID counselorId;

    @Schema(defaultValue = "0", minimum = "0")
    private int page = 0;

    @Schema(defaultValue = "10", minimum = "1", maximum = "100")
    private int size = 10;
}
