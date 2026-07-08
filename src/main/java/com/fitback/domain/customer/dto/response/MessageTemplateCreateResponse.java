package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.MessageDeliveryStatus;
import com.fitback.domain.customer.enums.MessageTonePreset;
import com.fitback.domain.customer.enums.MessageVersionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class MessageTemplateCreateResponse {

    private UUID messageTemplateId;
    private UUID customerId;
    private UUID followUpId;
    private String content;
    private MessageVersionType versionType;
    private MessageTonePreset tonePreset;
    private MessageDeliveryStatus deliveryStatus;
    private OffsetDateTime generatedAt;
}
