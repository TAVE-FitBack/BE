package com.fitback.domain.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitback.domain.schedule.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class TaskChecklistResponse {

    private UUID taskId;
    private UUID scheduleId;
    private String title;
    private TaskType taskType;
    private LocalDate dueDate;

    @JsonProperty("isDone")
    private boolean isDone;

    private OffsetDateTime doneAt;
}
