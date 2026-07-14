package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.FollowUpStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class NextActionRegenerateResponse {

    private UUID customerId;
    private UUID oldFollowUpId;
    private FollowUpStatus oldFollowUpStatus;
    private UUID newFollowUpId;
    private FollowUpStatus newFollowUpStatus;
    private int contactRound;
    private LocalDate recommendContactDate;
    private Integer priorityScore;
}
