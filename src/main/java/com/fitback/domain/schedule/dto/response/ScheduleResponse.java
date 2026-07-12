package com.fitback.domain.schedule.dto.response;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.schedule.enums.ScheduleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ScheduleResponse {

    private UUID scheduleId;
    private ScheduleType scheduleType;
    private String title;
    private String customerName;
    private Gender gender;
    private String serviceName;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private String memo;
}
