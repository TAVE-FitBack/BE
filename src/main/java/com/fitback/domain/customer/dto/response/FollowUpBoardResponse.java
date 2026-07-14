package com.fitback.domain.customer.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class FollowUpBoardResponse {

    private String tab;
    private LocalDate baseDate;
    private List<FollowUpBoardColumnResponse> columns;
}
