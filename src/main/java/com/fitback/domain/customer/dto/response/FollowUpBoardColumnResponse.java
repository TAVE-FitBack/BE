package com.fitback.domain.customer.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FollowUpBoardColumnResponse {

    private int contactRound;
    private String title;
    private int count;
    private List<FollowUpBoardItemResponse> items;
}
