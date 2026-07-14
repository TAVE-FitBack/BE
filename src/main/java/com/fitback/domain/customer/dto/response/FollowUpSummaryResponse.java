package com.fitback.domain.customer.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FollowUpSummaryResponse {

    private long todayPendingCount;
    private long secondRoundPendingCount;
    private long overdueCount;
    private long thisMonthPendingCount;
}
