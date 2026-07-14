package com.fitback.domain.customer.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FollowUpEndedListResponse {

    private List<FollowUpEndedItemResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
