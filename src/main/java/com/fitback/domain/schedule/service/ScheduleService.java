package com.fitback.domain.schedule.service;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.schedule.dto.request.ScheduleCreateRequest;
import com.fitback.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.fitback.domain.schedule.dto.response.ScheduleListResponse;
import com.fitback.domain.schedule.dto.response.ScheduleDetailResponse;
import com.fitback.domain.schedule.dto.response.ScheduleResponse;
import com.fitback.domain.schedule.entity.Schedule;
import com.fitback.domain.schedule.enums.ScheduleType;
import com.fitback.domain.schedule.exception.ScheduleErrorCode;
import com.fitback.domain.schedule.repository.ScheduleRepository;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final TaskChecklistService taskChecklistService;

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

    public ScheduleDetailResponse getScheduleDetail(UUID storeId, UUID scheduleId) {
        validateStoreId(storeId);
        Schedule schedule = scheduleRepository.findByIdAndStore_Id(scheduleId, storeId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        return toScheduleDetailResponse(schedule);
    }

    @Transactional
    public ScheduleResponse createSchedule(UUID storeId, UUID userId, ScheduleCreateRequest request) {
        validateStoreId(storeId);
        User user = findStoreUser(userId, storeId);
        ScheduleType scheduleType = parseScheduleType(request.getScheduleType());
        Gender gender = parseGender(request.getGender());
        validateScheduleRequest(scheduleType, request.getCustomerName(), request.getStartAt(), request.getEndAt());

        Schedule schedule = Schedule.builder()
                .store(user.getStore())
                .createdBy(user)
                .title(generateTitle(scheduleType, request.getCustomerName()))
                .scheduleType(scheduleType)
                .customerName(normalizeBlankToNull(request.getCustomerName()))
                .gender(gender)
                .serviceName(normalizeBlankToNull(request.getServiceName()))
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .memo(normalizeBlankToNull(request.getMemo()))
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);
        taskChecklistService.createForSchedule(savedSchedule);

        return toScheduleResponse(savedSchedule);
    }

    @Transactional
    public ScheduleResponse updateSchedule(UUID storeId, UUID scheduleId, ScheduleUpdateRequest request) {
        validateStoreId(storeId);
        Schedule schedule = scheduleRepository.findByIdAndStore_Id(scheduleId, storeId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        ScheduleType scheduleType = parseScheduleType(request.getScheduleType());
        Gender gender = parseGender(request.getGender());
        validateScheduleRequest(scheduleType, request.getCustomerName(), request.getStartAt(), request.getEndAt());

        schedule.update(
                generateTitle(scheduleType, request.getCustomerName()),
                scheduleType,
                normalizeBlankToNull(request.getCustomerName()),
                gender,
                normalizeBlankToNull(request.getServiceName()),
                request.getStartAt(),
                request.getEndAt(),
                normalizeBlankToNull(request.getMemo())
        );

        taskChecklistService.syncForUpdatedSchedule(schedule);

        return toScheduleResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(UUID storeId, UUID scheduleId) {
        validateStoreId(storeId);
        Schedule schedule = scheduleRepository.findByIdAndStore_Id(scheduleId, storeId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        scheduleRepository.delete(schedule);
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

    private User findStoreUser(UUID userId, UUID storeId) {
        if (userId == null) {
            throw new BusinessException(ScheduleErrorCode.USER_NOT_FOUND);
        }
        return userRepository.findByIdAndStore_Id(userId, storeId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.USER_NOT_FOUND));
    }

    private ScheduleType parseScheduleType(String value) {
        String normalized = normalizeRequired(value, ScheduleErrorCode.INVALID_SCHEDULE_TYPE);
        try {
            return ScheduleType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ScheduleErrorCode.INVALID_SCHEDULE_TYPE);
        }
    }

    private Gender parseGender(String value) {
        String normalized = normalizeBlankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Gender.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ScheduleErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateScheduleRequest(
            ScheduleType scheduleType,
            String customerName,
            OffsetDateTime startAt,
            OffsetDateTime endAt
    ) {
        if (startAt == null || endAt == null) {
            throw new BusinessException(ScheduleErrorCode.INVALID_INPUT_VALUE);
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_DATE_RANGE);
        }
        if (scheduleType == ScheduleType.CONSULTATION && normalizeBlankToNull(customerName) == null) {
            throw new BusinessException(ScheduleErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String generateTitle(ScheduleType scheduleType, String customerName) {
        String normalizedCustomerName = normalizeBlankToNull(customerName);
        return switch (scheduleType) {
            case CONSULTATION -> normalizedCustomerName + " 상담";
            case VISIT -> normalizedCustomerName == null ? "방문" : normalizedCustomerName + " 방문";
            case ETC -> "기타 일정";
        };
    }

    private String normalizeRequired(String value, ScheduleErrorCode errorCode) {
        String normalized = normalizeBlankToNull(value);
        if (normalized == null) {
            throw new BusinessException(errorCode);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private ScheduleDetailResponse toScheduleDetailResponse(Schedule schedule) {
        return ScheduleDetailResponse.builder()
                .scheduleId(schedule.getId())
                .scheduleType(schedule.getScheduleType())
                .title(schedule.getTitle())
                .customerName(schedule.getCustomerName())
                .gender(schedule.getGender())
                .serviceName(schedule.getServiceName())
                .startAt(schedule.getStartAt())
                .endAt(schedule.getEndAt())
                .memo(schedule.getMemo())
                .createdBy(schedule.getCreatedBy().getNickname())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
