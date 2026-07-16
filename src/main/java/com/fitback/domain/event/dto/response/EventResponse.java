package com.fitback.domain.event.dto.response;

import com.fitback.domain.event.entity.Event;
import com.fitback.domain.event.enums.EventStatus;
import com.fitback.domain.event.enums.EventType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class EventResponse {

    private UUID id;
    private String title;
    private EventType eventType;
    private String description;
    private BigDecimal discountRate;
    private UUID serviceId;
    private LocalDate startDate;
    private LocalDate endDate;
    private EventStatus status;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .eventType(event.getEventType())
                .description(event.getDescription())
                .discountRate(event.getDiscountRate())
                .serviceId(event.getService() != null ? event.getService().getId() : null)
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .status(event.getStatus())
                .build();
    }
}