package com.fitback.domain.customer.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class MessageTemplateMarkSentRequest {

    private OffsetDateTime sentAt;
}
