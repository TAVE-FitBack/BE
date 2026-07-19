package com.fitback.domain.schedule.service;

import com.fitback.domain.schedule.dto.response.TaskChecklistListResponse;
import com.fitback.domain.schedule.dto.response.TaskChecklistResponse;
import com.fitback.domain.schedule.entity.Schedule;
import com.fitback.domain.schedule.entity.TaskChecklist;
import com.fitback.domain.schedule.enums.ScheduleType;
import com.fitback.domain.schedule.enums.TaskType;
import com.fitback.domain.schedule.exception.TaskChecklistErrorCode;
import com.fitback.domain.schedule.repository.TaskChecklistRepository;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskChecklistServiceTest {

    @Mock
    private TaskChecklistRepository taskChecklistRepository;

    private TaskChecklistService taskChecklistService;

    @BeforeEach
    void setUp() {
        taskChecklistService = new TaskChecklistService(taskChecklistRepository);
    }

    @Test
    @DisplayName("상담 일정 생성 시 체크리스트를 자동 생성한다")
    void createForConsultationSchedule() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(
                scheduleId,
                store(UUID.randomUUID()),
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00")
        );

        when(taskChecklistRepository.findBySchedule_Id(scheduleId)).thenReturn(Optional.empty());

        taskChecklistService.createForSchedule(schedule);

        ArgumentCaptor<TaskChecklist> captor = ArgumentCaptor.forClass(TaskChecklist.class);
        verify(taskChecklistRepository).save(captor.capture());
        TaskChecklist saved = captor.getValue();
        assertThat(saved.getSchedule()).isEqualTo(schedule);
        assertThat(saved.getCustomer()).isNull();
        assertThat(saved.getTitle()).isEqualTo("김민지 상담 10:30");
        assertThat(saved.getTaskType()).isEqualTo(TaskType.CONSULTATION);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(saved.isDone()).isFalse();
        assertThat(saved.getDoneAt()).isNull();
    }

    @Test
    @DisplayName("방문 일정 생성 시 체크리스트를 자동 생성한다")
    void createForVisitSchedule() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(
                scheduleId,
                store(UUID.randomUUID()),
                ScheduleType.VISIT,
                "김민지 방문",
                "김민지",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00")
        );

        when(taskChecklistRepository.findBySchedule_Id(scheduleId)).thenReturn(Optional.empty());

        taskChecklistService.createForSchedule(schedule);

        ArgumentCaptor<TaskChecklist> captor = ArgumentCaptor.forClass(TaskChecklist.class);
        verify(taskChecklistRepository).save(captor.capture());
        TaskChecklist saved = captor.getValue();
        assertThat(saved.getSchedule()).isEqualTo(schedule);
        assertThat(saved.getCustomer()).isNull();
        assertThat(saved.getTitle()).isEqualTo("김민지 방문 10:30");
        assertThat(saved.getTaskType()).isEqualTo(TaskType.VISIT);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(saved.isDone()).isFalse();
        assertThat(saved.getDoneAt()).isNull();
    }

    @Test
    @DisplayName("기타 일정 생성 시 체크리스트를 자동 생성한다")
    void createForEtcSchedule() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(
                scheduleId,
                store(UUID.randomUUID()),
                ScheduleType.ETC,
                "기타 일정",
                null,
                OffsetDateTime.parse("2026-10-15T13:00:00+09:00")
        );

        when(taskChecklistRepository.findBySchedule_Id(scheduleId)).thenReturn(Optional.empty());

        taskChecklistService.createForSchedule(schedule);

        ArgumentCaptor<TaskChecklist> captor = ArgumentCaptor.forClass(TaskChecklist.class);
        verify(taskChecklistRepository).save(captor.capture());
        TaskChecklist saved = captor.getValue();
        assertThat(saved.getSchedule()).isEqualTo(schedule);
        assertThat(saved.getCustomer()).isNull();
        assertThat(saved.getTitle()).isEqualTo("기타 일정 13:00");
        assertThat(saved.getTaskType()).isEqualTo(TaskType.ETC);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(saved.isDone()).isFalse();
        assertThat(saved.getDoneAt()).isNull();
    }

    @Test
    @DisplayName("상담 일정 수정 시 체크리스트 제목과 날짜를 갱신한다")
    void syncUpdatedConsultationSchedule() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(
                scheduleId,
                store(UUID.randomUUID()),
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                OffsetDateTime.parse("2026-10-16T14:00:00+09:00")
        );
        TaskChecklist taskChecklist = taskChecklist(
                UUID.randomUUID(),
                schedule,
                "김민지 상담 10:30",
                TaskType.CONSULTATION,
                LocalDate.of(2026, 10, 15),
                false,
                null
        );

        when(taskChecklistRepository.findBySchedule_Id(scheduleId)).thenReturn(Optional.of(taskChecklist));

        taskChecklistService.syncForUpdatedSchedule(schedule);

        assertThat(taskChecklist.getTitle()).isEqualTo("김민지 상담 14:00");
        assertThat(taskChecklist.getTaskType()).isEqualTo(TaskType.CONSULTATION);
        assertThat(taskChecklist.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        verify(taskChecklistRepository, never()).save(org.mockito.ArgumentMatchers.any(TaskChecklist.class));
    }

    @Test
    @DisplayName("상담 일정이 방문 일정으로 변경되면 연결 체크리스트를 방문 유형으로 동기화한다")
    void syncChecklistWhenConsultationChangedToVisit() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(
                scheduleId,
                store(UUID.randomUUID()),
                ScheduleType.VISIT,
                "방문",
                null,
                OffsetDateTime.parse("2026-10-16T14:00:00+09:00")
        );
        TaskChecklist taskChecklist = taskChecklist(
                UUID.randomUUID(),
                schedule,
                "김민지 상담 10:30",
                TaskType.CONSULTATION,
                LocalDate.of(2026, 10, 15),
                false,
                null
        );

        when(taskChecklistRepository.findBySchedule_Id(scheduleId)).thenReturn(Optional.of(taskChecklist));

        taskChecklistService.syncForUpdatedSchedule(schedule);

        assertThat(taskChecklist.getTitle()).isEqualTo("방문 14:00");
        assertThat(taskChecklist.getTaskType()).isEqualTo(TaskType.VISIT);
        assertThat(taskChecklist.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        verify(taskChecklistRepository, never()).deleteBySchedule_Id(scheduleId);
        verify(taskChecklistRepository, never()).save(org.mockito.ArgumentMatchers.any(TaskChecklist.class));
    }

    @Test
    @DisplayName("방문 일정이 상담 일정으로 변경되면 체크리스트를 새로 생성한다")
    void createChecklistWhenVisitChangedToConsultation() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(
                scheduleId,
                store(UUID.randomUUID()),
                ScheduleType.CONSULTATION,
                "강호윤 상담",
                "강호윤",
                OffsetDateTime.parse("2026-10-17T09:00:00+09:00")
        );

        when(taskChecklistRepository.findBySchedule_Id(scheduleId)).thenReturn(Optional.empty());

        taskChecklistService.syncForUpdatedSchedule(schedule);

        ArgumentCaptor<TaskChecklist> captor = ArgumentCaptor.forClass(TaskChecklist.class);
        verify(taskChecklistRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("강호윤 상담 09:00");
        assertThat(captor.getValue().getTaskType()).isEqualTo(TaskType.CONSULTATION);
        assertThat(captor.getValue().getDueDate()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    @DisplayName("선택 날짜의 체크리스트를 조회한다")
    void getTaskChecklists() {
        UUID storeId = UUID.randomUUID();
        Store store = store(storeId);
        LocalDate date = LocalDate.of(2026, 10, 15);
        Schedule schedule = schedule(
                UUID.randomUUID(),
                store,
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00")
        );
        TaskChecklist taskChecklist = taskChecklist(
                UUID.randomUUID(),
                schedule,
                "김민지 상담 10:30",
                TaskType.CONSULTATION,
                date,
                false,
                null
        );

        when(taskChecklistRepository.findAllByDateAndTaskTypeAndStoreIdOrderByScheduleStartAt(
                date,
                TaskType.CONSULTATION,
                storeId
        )).thenReturn(List.of(taskChecklist));

        TaskChecklistListResponse response = taskChecklistService.getTaskChecklists(storeId, date);

        assertThat(response.getDate()).isEqualTo(date);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getTaskId()).isEqualTo(taskChecklist.getId());
        assertThat(response.getItems().get(0).getScheduleId()).isEqualTo(schedule.getId());
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("김민지 상담 10:30");
        assertThat(response.getItems().get(0).isDone()).isFalse();
        verify(taskChecklistRepository).findAllByDateAndTaskTypeAndStoreIdOrderByScheduleStartAt(
                date,
                TaskType.CONSULTATION,
                storeId
        );
    }

    @Test
    @DisplayName("체크리스트 완료 처리에 성공한다")
    void updateTaskDoneTrue() {
        UUID storeId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Schedule schedule = schedule(
                UUID.randomUUID(),
                store(storeId),
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00")
        );
        TaskChecklist taskChecklist = taskChecklist(
                taskId,
                schedule,
                "김민지 상담 10:30",
                TaskType.CONSULTATION,
                LocalDate.of(2026, 10, 15),
                false,
                null
        );

        when(taskChecklistRepository.findByIdWithSchedule(taskId)).thenReturn(Optional.of(taskChecklist));

        TaskChecklistResponse response = taskChecklistService.updateTaskDone(storeId, taskId, true);

        assertThat(response.isDone()).isTrue();
        assertThat(response.getDoneAt()).isNotNull();
        assertThat(taskChecklist.isDone()).isTrue();
        assertThat(taskChecklist.getDoneAt()).isNotNull();
    }

    @Test
    @DisplayName("체크리스트 완료 해제에 성공한다")
    void updateTaskDoneFalse() {
        UUID storeId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        OffsetDateTime doneAt = OffsetDateTime.parse("2026-10-15T10:40:00+09:00");
        Schedule schedule = schedule(
                UUID.randomUUID(),
                store(storeId),
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00")
        );
        TaskChecklist taskChecklist = taskChecklist(
                taskId,
                schedule,
                "김민지 상담 10:30",
                TaskType.CONSULTATION,
                LocalDate.of(2026, 10, 15),
                true,
                doneAt
        );

        when(taskChecklistRepository.findByIdWithSchedule(taskId)).thenReturn(Optional.of(taskChecklist));

        TaskChecklistResponse response = taskChecklistService.updateTaskDone(storeId, taskId, false);

        assertThat(response.isDone()).isFalse();
        assertThat(response.getDoneAt()).isNull();
        assertThat(taskChecklist.isDone()).isFalse();
        assertThat(taskChecklist.getDoneAt()).isNull();
    }

    @Test
    @DisplayName("다른 매장 체크리스트 접근은 TASK_CHECKLIST_NOT_FOUND 예외로 차단한다")
    void otherStoreTaskChecklistAccessDenied() {
        UUID storeId = UUID.randomUUID();
        UUID otherStoreId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Schedule schedule = schedule(
                UUID.randomUUID(),
                store(otherStoreId),
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00")
        );
        TaskChecklist taskChecklist = taskChecklist(
                taskId,
                schedule,
                "김민지 상담 10:30",
                TaskType.CONSULTATION,
                LocalDate.of(2026, 10, 15),
                false,
                null
        );

        when(taskChecklistRepository.findByIdWithSchedule(taskId)).thenReturn(Optional.of(taskChecklist));

        assertThatThrownBy(() -> taskChecklistService.updateTaskDone(storeId, taskId, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TaskChecklistErrorCode.TASK_CHECKLIST_NOT_FOUND);
    }

    private TaskChecklist taskChecklist(
            UUID taskId,
            Schedule schedule,
            String title,
            TaskType taskType,
            LocalDate dueDate,
            boolean done,
            OffsetDateTime doneAt
    ) {
        return TaskChecklist.builder()
                .id(taskId)
                .schedule(schedule)
                .title(title)
                .taskType(taskType)
                .dueDate(dueDate)
                .done(done)
                .doneAt(doneAt)
                .build();
    }

    private Schedule schedule(
            UUID scheduleId,
            Store store,
            ScheduleType scheduleType,
            String title,
            String customerName,
            OffsetDateTime startAt
    ) {
        return Schedule.builder()
                .id(scheduleId)
                .store(store)
                .title(title)
                .scheduleType(scheduleType)
                .customerName(customerName)
                .startAt(startAt)
                .endAt(startAt.plusMinutes(30))
                .build();
    }

    private Store store(UUID storeId) {
        Store store = Store.builder()
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
        ReflectionTestUtils.setField(store, "id", storeId);
        return store;
    }
}
