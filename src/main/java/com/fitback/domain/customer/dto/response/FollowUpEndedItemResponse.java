package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class FollowUpEndedItemResponse {

    private UUID followUpId;
    private UUID customerId;
    private String customerName;
    private String phoneNum;
    private Gender gender;
    private String serviceName;
    private CustomerStatus customerStatus;
    private FollowUpStatus followUpStatus;
    private int contactRound;
    private boolean hasReply;
    private OffsetDateTime repliedAt;
    private UUID latestMessageTemplateId;
    private String latestMessageDeliveryStatus;
    private OffsetDateTime latestContactAt;
    private String memo;
    private List<NonConversionReasonInfo> nonConversionReasons;
    private boolean followUpCompleted;

    @Getter
    @Builder
    public static class NonConversionReasonInfo {
        private String reasonType;
        private String displayName;
    }
}
