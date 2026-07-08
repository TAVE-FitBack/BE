package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.MessageTonePreset;
import com.fitback.domain.customer.enums.MessageVersionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class MessageTemplateOptionsResponse {

    private List<TonePresetOption> tonePresets;
    private List<VersionTypeOption> versionTypes;
    private List<EventOption> events;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class TonePresetOption {
        private MessageTonePreset tonePreset;
        private String label;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class VersionTypeOption {
        private MessageVersionType versionType;
        private String label;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class EventOption {
        private UUID eventId;
        private String title;
        private String eventType;
        private String description;
        private BigDecimal discountRate;
        private LocalDate startDate;
        private LocalDate endDate;
    }
}
