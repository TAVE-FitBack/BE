package com.fitback.domain.schedule.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class ScheduleCreateRequest {

    @NotBlank(message = "일정 유형은 필수입니다.")
    @Schema(
            description = "Schedule type",
            allowableValues = {"CONSULTATION", "VISIT", "ETC"},
            example = "CONSULTATION"
    )
    private String scheduleType;

    private String customerName;

    private String gender;

    private String serviceName;

    @NotNull(message = "일정 시작 일시는 필수입니다.")
    private OffsetDateTime startAt;

    @NotNull(message = "일정 종료 일시는 필수입니다.")
    private OffsetDateTime endAt;

    private String memo;
}
