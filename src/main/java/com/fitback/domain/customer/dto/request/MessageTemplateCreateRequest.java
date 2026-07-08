package com.fitback.domain.customer.dto.request;

import com.fitback.domain.customer.enums.MessageTonePreset;
import com.fitback.domain.customer.enums.MessageVersionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class MessageTemplateCreateRequest {

    @NotNull
    private UUID followUpId;

    @NotNull
    private MessageTonePreset tonePreset;

    @NotNull
    private MessageVersionType versionType;

    private UUID eventId;

    @Size(max = 500)
    private String additionalInstruction;
}
