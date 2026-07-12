package com.fitback.domain.schedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class TaskChecklistListResponse {

    private LocalDate date;
    private List<TaskChecklistResponse> items;
}
