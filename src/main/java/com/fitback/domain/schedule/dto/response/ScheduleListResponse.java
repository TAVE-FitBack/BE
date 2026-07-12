package com.fitback.domain.schedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ScheduleListResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private List<ScheduleResponse> schedules;
}
