package com.fitback.domain.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class FollowUpReplyUpdateResponse {

    private UUID followUpId;
    private boolean hasReply;
    private OffsetDateTime repliedAt;
}
