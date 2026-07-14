package com.fitback.domain.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowUpBoardQuery {

    @Schema(description = "보드 탭", allowableValues = {"TODAY", "SCHEDULED"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String tab;

    @Schema(description = "고객명 또는 연락처 검색어")
    private String keyword;

    @Schema(description = "답장 유무")
    private Boolean hasReply;

    @Schema(description = "후속 연락 차수", allowableValues = {"1", "2", "3"})
    private Integer contactRound;
}
