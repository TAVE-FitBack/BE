package com.fitback.domain.schedule.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TaskChecklistUpdateRequest {

    @NotNull(message = "완료 여부는 필수입니다.")
    private Boolean isDone;
}
