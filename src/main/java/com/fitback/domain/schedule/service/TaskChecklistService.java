package com.fitback.domain.schedule.service;

import com.fitback.domain.schedule.dto.response.TaskChecklistListResponse;
import com.fitback.domain.schedule.dto.response.TaskChecklistResponse;
import com.fitback.domain.schedule.entity.Schedule;
import com.fitback.domain.schedule.entity.TaskChecklist;
import com.fitback.domain.schedule.enums.ScheduleType;
import com.fitback.domain.schedule.enums.TaskType;
import com.fitback.domain.schedule.exception.TaskChecklistErrorCode;
import com.fitback.domain.schedule.repository.TaskChecklistRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskChecklistService {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter CHECKLIST_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TaskChecklistRepository taskChecklistRepository;

    public TaskChecklistListResponse getTaskChecklists(UUID storeId, LocalDate date) {
        validateStoreId(storeId);
        if (date == null) {
            throw new BusinessException(TaskChecklistErrorCode.INVALID_INPUT_VALUE);
        }

        List<TaskChecklistResponse> items = taskChecklistRepository
                .findAllByDateAndTaskTypeAndStoreIdOrderByScheduleStartAt(
                        date,
                        TaskType.CONSULTATION,
                        storeId
                )
                .stream()
                .map(this::toResponse)
                .toList();

        return TaskChecklistListResponse.builder()
                .date(date)
                .items(items)
                .build();
    }

    @Transactional
    public TaskChecklistResponse updateTaskDone(UUID storeId, UUID taskId, boolean done) {
        validateStoreId(storeId);
        if (taskId == null) {
            throw new BusinessException(TaskChecklistErrorCode.INVALID_INPUT_VALUE);
        }

        TaskChecklist taskChecklist = taskChecklistRepository.findByIdWithSchedule(taskId)
                .orElseThrow(() -> new BusinessException(TaskChecklistErrorCode.TASK_CHECKLIST_NOT_FOUND));
        validateScheduleStore(taskChecklist, storeId);

        OffsetDateTime doneAt = done ? OffsetDateTime.now(SERVICE_ZONE_ID) : null;
        taskChecklist.updateDone(done, doneAt);

        return toResponse(taskChecklist);
    }

    @Transactional
    public void createForSchedule(Schedule schedule) {
        if (schedule == null) {
            return;
        }
        if (taskChecklistRepository.findBySchedule_Id(schedule.getId()).isPresent()) {
            return;
        }

        taskChecklistRepository.save(TaskChecklist.builder()
                .schedule(schedule)
                .customer(null)
                .title(generateTitle(schedule))
                .taskType(toTaskType(schedule.getScheduleType()))
                .dueDate(toDueDate(schedule))
                .done(false)
                .doneAt(null)
                .build());
    }

    @Transactional
    public void syncForUpdatedSchedule(Schedule schedule) {
        if (schedule == null) {
            return;
        }

        if (schedule.getScheduleType() != ScheduleType.CONSULTATION) {
            taskChecklistRepository.deleteBySchedule_Id(schedule.getId());
            return;
        }

        taskChecklistRepository.findBySchedule_Id(schedule.getId())
                .ifPresentOrElse(
                        taskChecklist -> taskChecklist.syncFromSchedule(generateTitle(schedule), toDueDate(schedule)),
                        () -> createForSchedule(schedule)
                );
    }

    @Transactional
    public void deleteBySchedule(Schedule schedule) {
        if (schedule == null) {
            return;
        }
        taskChecklistRepository.deleteBySchedule_Id(schedule.getId());
    }

    private void validateStoreId(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(TaskChecklistErrorCode.STORE_NOT_ASSIGNED);
        }
    }

    private void validateScheduleStore(TaskChecklist taskChecklist, UUID storeId) {
        if (!storeId.equals(taskChecklist.getSchedule().getStore().getId())) {
            throw new BusinessException(TaskChecklistErrorCode.TASK_CHECKLIST_NOT_FOUND);
        }
    }

    private String generateTitle(Schedule schedule) {
        String title = normalizeBlankToNull(schedule.getTitle());
        if (title == null) {
            title = normalizeBlankToNull(schedule.getCustomerName());
        }
        if (title == null) {
            title = schedule.getScheduleType().name();
        }

        String time = schedule.getStartAt().toLocalTime().format(CHECKLIST_TIME_FORMATTER);
        return title + " " + time;
    }

    private LocalDate toDueDate(Schedule schedule) {
        return schedule.getStartAt().toLocalDate();
    }

    private TaskType toTaskType(ScheduleType scheduleType) {
        return switch (scheduleType) {
            case CONSULTATION -> TaskType.CONSULTATION;
            case VISIT -> TaskType.VISIT;
            case ETC -> TaskType.ETC;
        };
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private TaskChecklistResponse toResponse(TaskChecklist taskChecklist) {
        return TaskChecklistResponse.builder()
                .taskId(taskChecklist.getId())
                .scheduleId(taskChecklist.getSchedule().getId())
                .title(taskChecklist.getTitle())
                .taskType(taskChecklist.getTaskType())
                .dueDate(taskChecklist.getDueDate())
                .isDone(taskChecklist.isDone())
                .doneAt(taskChecklist.getDoneAt())
                .build();
    }
}
