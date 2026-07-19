package com.fitback.domain.schedule.service;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.schedule.dto.request.ScheduleCreateRequest;
import com.fitback.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.fitback.domain.schedule.dto.response.ScheduleDetailResponse;
import com.fitback.domain.schedule.dto.response.ScheduleListResponse;
import com.fitback.domain.schedule.dto.response.ScheduleResponse;
import com.fitback.domain.schedule.entity.Schedule;
import com.fitback.domain.schedule.enums.ScheduleType;
import com.fitback.domain.schedule.exception.ScheduleErrorCode;
import com.fitback.domain.schedule.repository.ScheduleRepository;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.domain.user.repository.UserRepository;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskChecklistService taskChecklistService;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, userRepository, taskChecklistService);
    }

    @Test
    @DisplayName("선택 기간과 겹치는 일정 목록을 조회한다")
    void getSchedules() {
        UUID storeId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 10, 12);
        LocalDate endDate = LocalDate.of(2026, 10, 18);
        Schedule schedule = schedule(
                UUID.randomUUID(),
                store(storeId),
                user(UUID.randomUUID(), store(storeId), "문형주"),
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                Gender.FEMALE,
                "PT",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00"),
                OffsetDateTime.parse("2026-10-15T11:00:00+09:00"),
                "가격 문의가 많음"
        );

        when(scheduleRepository.findOverlappingSchedules(
                storeId,
                OffsetDateTime.parse("2026-10-12T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-19T00:00:00+09:00")
        )).thenReturn(List.of(schedule));

        ScheduleListResponse response = scheduleService.getSchedules(storeId, startDate, endDate);

        assertThat(response.getStartDate()).isEqualTo(startDate);
        assertThat(response.getEndDate()).isEqualTo(endDate);
        assertThat(response.getSchedules()).hasSize(1);
        assertThat(response.getSchedules().get(0).getScheduleId()).isEqualTo(schedule.getId());
        assertThat(response.getSchedules().get(0).getScheduleType()).isEqualTo(ScheduleType.CONSULTATION);
        verify(scheduleRepository).findOverlappingSchedules(
                storeId,
                OffsetDateTime.parse("2026-10-12T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-19T00:00:00+09:00")
        );
    }

    @Test
    @DisplayName("일정 상세 조회는 createdBy를 생성자 닉네임으로 반환한다")
    void getScheduleDetail() {
        UUID storeId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Store store = store(storeId);
        User creator = user(UUID.randomUUID(), store, "문형주");
        Schedule schedule = schedule(
                scheduleId,
                store,
                creator,
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                Gender.FEMALE,
                "PT",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00"),
                OffsetDateTime.parse("2026-10-15T11:00:00+09:00"),
                "가격 문의가 많음"
        );
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 14, 18, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 14, 19, 0);
        ReflectionTestUtils.setField(schedule, "createdAt", createdAt);
        ReflectionTestUtils.setField(schedule, "updatedAt", updatedAt);

        when(scheduleRepository.findByIdAndStore_Id(scheduleId, storeId)).thenReturn(Optional.of(schedule));

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(storeId, scheduleId);

        assertThat(response.getScheduleId()).isEqualTo(scheduleId);
        assertThat(response.getCreatedBy()).isEqualTo("문형주");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
        verify(scheduleRepository).findByIdAndStore_Id(scheduleId, storeId);
    }

    @Test
    @DisplayName("일정을 등록한다")
    void createSchedule() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Store store = store(storeId);
        User creator = user(userId, store, "문형주");
        ScheduleCreateRequest request = createRequest(
                "CONSULTATION",
                "김민지",
                "FEMALE",
                "PT",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00"),
                OffsetDateTime.parse("2026-10-15T11:00:00+09:00"),
                "가격 문의가 많음"
        );

        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(creator));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", scheduleId);
            return schedule;
        });

        ScheduleResponse response = scheduleService.createSchedule(storeId, userId, request);

        assertThat(response.getScheduleId()).isEqualTo(scheduleId);
        assertThat(response.getScheduleType()).isEqualTo(ScheduleType.CONSULTATION);
        assertThat(response.getTitle()).isEqualTo("김민지 상담");
        assertThat(response.getCustomerName()).isEqualTo("김민지");
        assertThat(response.getGender()).isEqualTo(Gender.FEMALE);

        ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(scheduleCaptor.capture());
        Schedule saved = scheduleCaptor.getValue();
        assertThat(saved.getStore()).isEqualTo(store);
        assertThat(saved.getCreatedBy()).isEqualTo(creator);
        assertThat(saved.getCustomer()).isNull();
        assertThat(saved.getConsultation()).isNull();
        assertThat(saved.getTitle()).isEqualTo("김민지 상담");
        verify(taskChecklistService).createForSchedule(saved);
    }

    @Test
    @DisplayName("일정을 수정한다")
    void updateSchedule() {
        UUID storeId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Store store = store(storeId);
        Schedule schedule = schedule(
                scheduleId,
                store,
                user(UUID.randomUUID(), store, "문형주"),
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                Gender.FEMALE,
                "PT",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00"),
                OffsetDateTime.parse("2026-10-15T11:00:00+09:00"),
                "가격 문의가 많음"
        );
        ScheduleUpdateRequest request = updateRequest(
                "VISIT",
                "",
                null,
                "필라테스",
                OffsetDateTime.parse("2026-10-16T13:00:00+09:00"),
                OffsetDateTime.parse("2026-10-16T13:30:00+09:00"),
                "방문으로 변경"
        );

        when(scheduleRepository.findByIdAndStore_Id(scheduleId, storeId)).thenReturn(Optional.of(schedule));

        ScheduleResponse response = scheduleService.updateSchedule(storeId, scheduleId, request);

        assertThat(response.getScheduleId()).isEqualTo(scheduleId);
        assertThat(response.getScheduleType()).isEqualTo(ScheduleType.VISIT);
        assertThat(response.getTitle()).isEqualTo("방문");
        assertThat(response.getCustomerName()).isNull();
        assertThat(response.getGender()).isNull();
        assertThat(response.getServiceName()).isEqualTo("필라테스");
        assertThat(schedule.getTitle()).isEqualTo("방문");
        verify(scheduleRepository).findByIdAndStore_Id(scheduleId, storeId);
        verify(taskChecklistService).syncForUpdatedSchedule(schedule);
    }

    @Test
    @DisplayName("일정을 삭제한다")
    void deleteSchedule() {
        UUID storeId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Store store = store(storeId);
        Schedule schedule = schedule(scheduleId, store, user(UUID.randomUUID(), store, "문형주"));

        when(scheduleRepository.findByIdAndStore_Id(scheduleId, storeId)).thenReturn(Optional.of(schedule));

        scheduleService.deleteSchedule(storeId, scheduleId);

        verify(scheduleRepository).findByIdAndStore_Id(scheduleId, storeId);
        verify(scheduleRepository).delete(schedule);
        verify(taskChecklistService, never()).deleteBySchedule(any(Schedule.class));
    }

    @Test
    @DisplayName("다른 매장 일정 접근은 SCHEDULE_NOT_FOUND 예외로 차단한다")
    void otherStoreScheduleAccessDenied() {
        UUID storeId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        when(scheduleRepository.findByIdAndStore_Id(scheduleId, storeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getScheduleDetail(storeId, scheduleId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);

        verify(scheduleRepository).findByIdAndStore_Id(scheduleId, storeId);
    }

    @Test
    @DisplayName("잘못된 시간 범위는 INVALID_DATE_RANGE 예외가 발생한다")
    void invalidDateRange() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Store store = store(storeId);
        User creator = user(userId, store, "문형주");
        ScheduleCreateRequest request = createRequest(
                "CONSULTATION",
                "김민지",
                "FEMALE",
                "PT",
                OffsetDateTime.parse("2026-10-15T11:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T11:00:00+09:00"),
                null
        );

        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(creator));

        assertThatThrownBy(() -> scheduleService.createSchedule(storeId, userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_DATE_RANGE);

        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    private ScheduleCreateRequest createRequest(
            String scheduleType,
            String customerName,
            String gender,
            String serviceName,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String memo
    ) {
        ScheduleCreateRequest request = new ScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleType", scheduleType);
        ReflectionTestUtils.setField(request, "customerName", customerName);
        ReflectionTestUtils.setField(request, "gender", gender);
        ReflectionTestUtils.setField(request, "serviceName", serviceName);
        ReflectionTestUtils.setField(request, "startAt", startAt);
        ReflectionTestUtils.setField(request, "endAt", endAt);
        ReflectionTestUtils.setField(request, "memo", memo);
        return request;
    }

    private ScheduleUpdateRequest updateRequest(
            String scheduleType,
            String customerName,
            String gender,
            String serviceName,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String memo
    ) {
        ScheduleUpdateRequest request = new ScheduleUpdateRequest();
        ReflectionTestUtils.setField(request, "scheduleType", scheduleType);
        ReflectionTestUtils.setField(request, "customerName", customerName);
        ReflectionTestUtils.setField(request, "gender", gender);
        ReflectionTestUtils.setField(request, "serviceName", serviceName);
        ReflectionTestUtils.setField(request, "startAt", startAt);
        ReflectionTestUtils.setField(request, "endAt", endAt);
        ReflectionTestUtils.setField(request, "memo", memo);
        return request;
    }

    private Schedule schedule(UUID scheduleId, Store store, User creator) {
        return schedule(
                scheduleId,
                store,
                creator,
                ScheduleType.CONSULTATION,
                "김민지 상담",
                "김민지",
                Gender.FEMALE,
                "PT",
                OffsetDateTime.parse("2026-10-15T10:30:00+09:00"),
                OffsetDateTime.parse("2026-10-15T11:00:00+09:00"),
                "가격 문의가 많음"
        );
    }

    private Schedule schedule(
            UUID scheduleId,
            Store store,
            User creator,
            ScheduleType scheduleType,
            String title,
            String customerName,
            Gender gender,
            String serviceName,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String memo
    ) {
        return Schedule.builder()
                .id(scheduleId)
                .store(store)
                .createdBy(creator)
                .title(title)
                .scheduleType(scheduleType)
                .customerName(customerName)
                .gender(gender)
                .serviceName(serviceName)
                .startAt(startAt)
                .endAt(endAt)
                .memo(memo)
                .build();
    }

    private Store store(UUID storeId) {
        return Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
    }

    private User user(UUID userId, Store store, String nickname) {
        return User.builder()
                .id(userId)
                .store(store)
                .email(userId + "@fitback.test")
                .nickname(nickname)
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();
    }
}
