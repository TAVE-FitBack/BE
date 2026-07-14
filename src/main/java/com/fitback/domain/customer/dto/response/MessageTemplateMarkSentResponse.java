package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.MessageDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class MessageTemplateMarkSentResponse {

    private UUID messageTemplateId;
    private MessageDeliveryStatus deliveryStatus;
    private OffsetDateTime sentAt;
    private UUID followUpId;
    private FollowUpStatus followUpStatus;
    private int contactRound;
}
