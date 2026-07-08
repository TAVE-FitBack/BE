package com.fitback.domain.customer.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fitback.domain.customer.enums.MessageTonePreset;
import com.fitback.domain.customer.enums.MessageVersionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiMessageGenerateResponse {

    private String content;
    private MessageVersionType versionType;
    private MessageTonePreset tonePreset;
}
