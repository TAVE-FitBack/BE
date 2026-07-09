package com.fitback.domain.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ConsultationListQuery {

    @Schema(example = "2026-10", description = "조회 월(YYYY-MM). 미입력 시 현재 월")
    private String month;

    @Schema(description = "고객명, 연락처, 최신 상담 상품명 또는 관심 상품명 검색")
    private String keyword;

    @Schema(description = "성별", allowableValues = {"MALE", "FEMALE"})
    private String gender;

    @Schema(description = "최신 상담 상품 또는 관심 상품 ID")
    private UUID serviceId;

    @Schema(description = "방문경로 ID")
    private UUID inflowPathId;

    @Schema(
            description = "최신 상담 관리 단계",
            allowableValues = {"CONSULTATION", "FIRST_FOLLOW_UP", "SECOND_FOLLOW_UP", "TRIAL", "FINAL_DECISION"}
    )
    private String stage;

    @Schema(description = "미등록 사유 코드", example = "PRICE_BURDEN")
    private String reasonType;

    @Schema(
            description = "고객 등록 상태",
            allowableValues = {"REGISTERED", "PENDING", "SCHEDULED", "LOST", "NO_SHOW"}
    )
    private String status;

    @Schema(description = "고객온도", allowableValues = {"HOT", "WARM", "HOLD", "COLD", "LOST"})
    private String leadTemperature;

    @Schema(description = "담당자 ID")
    private UUID counselorId;

    @Schema(defaultValue = "0", minimum = "0")
    private int page = 0;

    @Schema(defaultValue = "10", minimum = "1", maximum = "100")
    private int size = 10;
}
