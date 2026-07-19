package com.fitback.domain.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitback.domain.schedule.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "체크리스트 유형. CONSULTATION, VISIT, ETC 중 하나입니다.", example = "CONSULTATION")
    private TaskType taskType;

    private LocalDate dueDate;

    @JsonProperty("isDone")
    private boolean isDone;

    private OffsetDateTime doneAt;
}
