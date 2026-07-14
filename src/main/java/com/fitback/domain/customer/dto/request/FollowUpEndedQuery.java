package com.fitback.domain.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FollowUpEndedQuery {

    @Schema(description = "고객명 또는 연락처 검색어")
    private String keyword;

    @Schema(description = "후속 연락 상태", allowableValues = {"COMPLETED", "CLOSED"})
    private String followUpStatus;

    @Schema(description = "후속 연락 차수", allowableValues = {"1", "2", "3"})
    private Integer contactRound;

    @Schema(description = "답장 유무")
    private Boolean hasReply;

    @Schema(description = "최근 연락일 검색 시작일. latestContactAt 기준", example = "2026-10-01")
    private LocalDate startDate;

    @Schema(description = "최근 연락일 검색 종료일. latestContactAt 기준", example = "2026-10-31")
    private LocalDate endDate;

    @Schema(defaultValue = "0", minimum = "0")
    private int page = 0;

    @Schema(defaultValue = "20", minimum = "1", maximum = "100")
    private int size = 20;
}
