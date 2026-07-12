package com.fitback.domain.schedule.service;

import com.fitback.domain.schedule.dto.response.ScheduleListResponse;
import com.fitback.domain.schedule.dto.response.ScheduleResponse;
import com.fitback.domain.schedule.entity.Schedule;
import com.fitback.domain.schedule.exception.ScheduleErrorCode;
import com.fitback.domain.schedule.repository.ScheduleRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final ScheduleRepository scheduleRepository;

    public ScheduleListResponse getSchedules(UUID storeId, LocalDate startDate, LocalDate endDate) {
        validateStoreId(storeId);
        validateDateRange(startDate, endDate);

        OffsetDateTime startInclusive = startDate.atStartOfDay(SERVICE_ZONE_ID).toOffsetDateTime();
        OffsetDateTime endExclusive = endDate.plusDays(1).atStartOfDay(SERVICE_ZONE_ID).toOffsetDateTime();

        List<ScheduleResponse> schedules = scheduleRepository
                .findOverlappingSchedules(storeId, startInclusive, endExclusive)
                .stream()
                .map(this::toScheduleResponse)
                .toList();

        return ScheduleListResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .schedules(schedules)
                .build();
    }

    private void validateStoreId(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(ScheduleErrorCode.STORE_NOT_ASSIGNED);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ScheduleErrorCode.INVALID_INPUT_VALUE);
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_DATE_RANGE);
        }
    }

    private ScheduleResponse toScheduleResponse(Schedule schedule) {
        return ScheduleResponse.builder()
                .scheduleId(schedule.getId())
                .scheduleType(schedule.getScheduleType())
                .title(schedule.getTitle())
                .customerName(schedule.getCustomerName())
                .gender(schedule.getGender())
                .serviceName(schedule.getServiceName())
                .startAt(schedule.getStartAt())
                .endAt(schedule.getEndAt())
                .memo(schedule.getMemo())
                .build();
    }
}
