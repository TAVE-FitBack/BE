package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class FollowUpBoardItemResponse {

    private UUID followUpId;
    private UUID customerId;
    private String customerName;
    private String phoneNum;
    private Gender gender;
    private String serviceName;
    private CustomerStatus customerStatus;
    private FollowUpStatus followUpStatus;
    private int contactRound;
    private LocalDate recommendContactDate;
    private String memo;
    private boolean hasReply;
    private OffsetDateTime repliedAt;
    private String leadTemperature;
    private Integer priorityScore;
    private List<NonConversionReasonInfo> nonConversionReasons;
    private String nextBestActionTitle;
    private String nextBestActionDescription;
    private UUID latestMessageTemplateId;
    private String latestMessageDeliveryStatus;
    private OffsetDateTime latestMessageGeneratedAt;

    @Getter
    @Builder
    public static class NonConversionReasonInfo {
        private String reasonType;
        private String displayName;
    }
}
