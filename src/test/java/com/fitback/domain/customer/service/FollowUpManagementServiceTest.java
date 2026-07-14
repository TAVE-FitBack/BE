package com.fitback.domain.customer.service;

import com.fitback.domain.customer.dto.request.FollowUpBoardQuery;
import com.fitback.domain.customer.dto.request.FollowUpEndedQuery;
import com.fitback.domain.customer.dto.response.FollowUpBoardResponse;
import com.fitback.domain.customer.dto.response.FollowUpEndedListResponse;
import com.fitback.domain.customer.dto.response.FollowUpSummaryResponse;
import com.fitback.domain.customer.exception.FollowUpManagementErrorCode;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.BoardCondition;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.BoardRow;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.EndedCondition;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.EndedPageRows;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.EndedRow;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.NonConversionReasonRow;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.SummaryCounts;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpManagementServiceTest {

    @Mock
    private FollowUpManagementQueryRepository queryRepository;

    @InjectMocks
    private FollowUpManagementService followUpManagementService;

    @Test
    @DisplayName("요약 카드 조회는 PENDING 후속 연락 집계 결과를 반환한다")
    void getSummary() {
        UUID storeId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any(), any()))
                .thenReturn(new SummaryCounts(12, 5, 3, 24));

        FollowUpSummaryResponse response = followUpManagementService.getSummary(storeId, "2026-10");

        assertThat(response.getTodayPendingCount()).isEqualTo(12);
        assertThat(response.getSecondRoundPendingCount()).isEqualTo(5);
        assertThat(response.getOverdueCount()).isEqualTo(3);
        assertThat(response.getThisMonthPendingCount()).isEqualTo(24);

        ArgumentCaptor<LocalDate> monthStartCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> monthEndCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(queryRepository).findSummaryCounts(
                eq(storeId),
                any(),
                monthStartCaptor.capture(),
                monthEndCaptor.capture()
        );
        assertThat(monthStartCaptor.getValue()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(monthEndCaptor.getValue()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("오늘 연락 보드는 TODAY 조건으로 조회하고 차수 컬럼을 반환한다")
    void getTodayBoard() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        FollowUpBoardQuery query = new FollowUpBoardQuery();
        query.setTab("TODAY");
        BoardRow row = boardRow(followUpId, customerId, 1, "김민지", 80, messageTemplateId);
        when(queryRepository.findBoardRows(eq(storeId), any(), any()))
                .thenReturn(List.of(row));
        when(queryRepository.findNonConversionReasons(anyCollection()))
                .thenReturn(List.of(new NonConversionReasonRow(customerId, "PRICE_BURDEN")));

        FollowUpBoardResponse response = followUpManagementService.getBoard(storeId, query);

        assertThat(response.getTab()).isEqualTo("TODAY");
        assertThat(response.getColumns()).hasSize(3);
        assertThat(response.getColumns().get(0).getCount()).isEqualTo(1);
        assertThat(response.getColumns().get(0).getItems()).singleElement().satisfies(item -> {
            assertThat(item.getFollowUpId()).isEqualTo(followUpId);
            assertThat(item.getCustomerName()).isEqualTo("김민지");
            assertThat(item.getLatestMessageTemplateId()).isEqualTo(messageTemplateId);
            assertThat(item.getNextBestActionTitle()).isEqualTo("부담 적은 시작 옵션 제안");
            assertThat(item.getNonConversionReasons()).singleElement()
                    .satisfies(reason -> assertThat(reason.getDisplayName()).isEqualTo("이용료 부담"));
        });

        ArgumentCaptor<BoardCondition> conditionCaptor = ArgumentCaptor.forClass(BoardCondition.class);
        verify(queryRepository).findBoardRows(eq(storeId), conditionCaptor.capture(), any());
        assertThat(conditionCaptor.getValue().tab()).isEqualTo("TODAY");
    }

    @Test
    @DisplayName("연락 예정 보드는 SCHEDULED 조건과 검색어를 적용한다")
    void getScheduledBoard() {
        UUID storeId = UUID.randomUUID();
        FollowUpBoardQuery query = new FollowUpBoardQuery();
        query.setTab("scheduled");
        query.setKeyword(" 010-1234 ");
        when(queryRepository.findBoardRows(eq(storeId), any(), any()))
                .thenReturn(List.of());

        FollowUpBoardResponse response = followUpManagementService.getBoard(storeId, query);

        assertThat(response.getTab()).isEqualTo("SCHEDULED");
        assertThat(response.getColumns()).hasSize(3);
        assertThat(response.getColumns()).allSatisfy(column -> assertThat(column.getItems()).isEmpty());

        ArgumentCaptor<BoardCondition> conditionCaptor = ArgumentCaptor.forClass(BoardCondition.class);
        verify(queryRepository).findBoardRows(eq(storeId), conditionCaptor.capture(), any());
        assertThat(conditionCaptor.getValue().tab()).isEqualTo("SCHEDULED");
        assertThat(conditionCaptor.getValue().keyword()).isEqualTo("010-1234");
    }

    @Test
    @DisplayName("보드 응답은 contactRound 기준으로 1차, 2차, 3차 컬럼에 그룹핑한다")
    void groupBoardByContactRound() {
        UUID storeId = UUID.randomUUID();
        FollowUpBoardQuery query = new FollowUpBoardQuery();
        query.setTab("TODAY");
        when(queryRepository.findBoardRows(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        boardRow(UUID.randomUUID(), UUID.randomUUID(), 1, "1차", 90, null),
                        boardRow(UUID.randomUUID(), UUID.randomUUID(), 2, "2차", 80, null),
                        boardRow(UUID.randomUUID(), UUID.randomUUID(), 2, "2차-2", 70, null),
                        boardRow(UUID.randomUUID(), UUID.randomUUID(), 3, "3차", 60, null)
                ));
        when(queryRepository.findNonConversionReasons(anyCollection())).thenReturn(List.of());

        FollowUpBoardResponse response = followUpManagementService.getBoard(storeId, query);

        assertThat(response.getColumns()).extracting("contactRound").containsExactly(1, 2, 3);
        assertThat(response.getColumns()).extracting("count").containsExactly(1, 2, 1);
    }

    @Test
    @DisplayName("종료 목록은 페이지 응답과 latestContactAt 기준 항목을 반환한다")
    void getEndedFollowUps() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        FollowUpEndedQuery query = new FollowUpEndedQuery();
        query.setPage(0);
        query.setSize(10);
        EndedRow row = endedRow(followUpId, customerId, "COMPLETED", true);
        when(queryRepository.findEndedRows(eq(storeId), any(), eq(0), eq(10)))
                .thenReturn(new EndedPageRows(List.of(row), 11));
        when(queryRepository.findNonConversionReasons(anyCollection()))
                .thenReturn(List.of(new NonConversionReasonRow(customerId, "SCHEDULE_CONFLICT")));

        FollowUpEndedListResponse response = followUpManagementService.getEndedFollowUps(storeId, query);

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(11);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getFollowUpId()).isEqualTo(followUpId);
            assertThat(item.getFollowUpStatus().name()).isEqualTo("COMPLETED");
            assertThat(item.getLatestContactAt()).isEqualTo(OffsetDateTime.parse("2026-10-15T13:20:00+09:00"));
            assertThat(item.isFollowUpCompleted()).isTrue();
            assertThat(item.getNonConversionReasons()).singleElement()
                    .satisfies(reason -> assertThat(reason.getDisplayName()).isEqualTo("일정 문제"));
        });
    }

    @Test
    @DisplayName("검색 조건과 답장 유무 필터는 보드 조회 조건으로 전달된다")
    void applyBoardSearchAndReplyFilter() {
        UUID storeId = UUID.randomUUID();
        FollowUpBoardQuery query = new FollowUpBoardQuery();
        query.setTab("TODAY");
        query.setKeyword(" 김민지 ");
        query.setHasReply(false);
        query.setContactRound(2);
        when(queryRepository.findBoardRows(eq(storeId), any(), any())).thenReturn(List.of());

        followUpManagementService.getBoard(storeId, query);

        ArgumentCaptor<BoardCondition> conditionCaptor = ArgumentCaptor.forClass(BoardCondition.class);
        verify(queryRepository).findBoardRows(eq(storeId), conditionCaptor.capture(), any());
        assertThat(conditionCaptor.getValue().keyword()).isEqualTo("김민지");
        assertThat(conditionCaptor.getValue().hasReply()).isFalse();
        assertThat(conditionCaptor.getValue().contactRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("종료 목록 검색 조건과 답장 유무 필터는 조회 조건으로 전달된다")
    void applyEndedSearchAndReplyFilter() {
        UUID storeId = UUID.randomUUID();
        FollowUpEndedQuery query = new FollowUpEndedQuery();
        query.setKeyword(" 010 ");
        query.setHasReply(true);
        query.setContactRound(3);
        query.setFollowUpStatus("closed");
        query.setStartDate(LocalDate.of(2026, 10, 1));
        query.setEndDate(LocalDate.of(2026, 10, 31));
        when(queryRepository.findEndedRows(eq(storeId), any(), eq(0), eq(20)))
                .thenReturn(new EndedPageRows(List.of(), 0));

        followUpManagementService.getEndedFollowUps(storeId, query);

        ArgumentCaptor<EndedCondition> conditionCaptor = ArgumentCaptor.forClass(EndedCondition.class);
        verify(queryRepository).findEndedRows(eq(storeId), conditionCaptor.capture(), eq(0), eq(20));
        assertThat(conditionCaptor.getValue().keyword()).isEqualTo("010");
        assertThat(conditionCaptor.getValue().hasReply()).isTrue();
        assertThat(conditionCaptor.getValue().contactRound()).isEqualTo(3);
        assertThat(conditionCaptor.getValue().followUpStatus()).isEqualTo("CLOSED");
        assertThat(conditionCaptor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(conditionCaptor.getValue().endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("로그인 사용자의 storeId를 Repository 조회 조건으로 전달해 다른 매장 데이터를 노출하지 않는다")
    void passStoreIdToRepository() {
        UUID storeId = UUID.randomUUID();
        FollowUpBoardQuery query = new FollowUpBoardQuery();
        query.setTab("TODAY");
        when(queryRepository.findBoardRows(eq(storeId), any(), any())).thenReturn(List.of());

        followUpManagementService.getBoard(storeId, query);

        verify(queryRepository).findBoardRows(eq(storeId), any(), any());
    }

    @Test
    @DisplayName("잘못된 tab 요청은 INVALID_INPUT_VALUE 예외가 발생한다")
    void rejectInvalidTab() {
        FollowUpBoardQuery query = new FollowUpBoardQuery();
        query.setTab("ENDED");

        assertFollowUpManagementError(
                () -> followUpManagementService.getBoard(UUID.randomUUID(), query),
                FollowUpManagementErrorCode.INVALID_INPUT_VALUE
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("잘못된 날짜 범위는 INVALID_DATE_RANGE 예외가 발생한다")
    void rejectInvalidDateRange() {
        FollowUpEndedQuery query = new FollowUpEndedQuery();
        query.setStartDate(LocalDate.of(2026, 10, 31));
        query.setEndDate(LocalDate.of(2026, 10, 1));

        assertFollowUpManagementError(
                () -> followUpManagementService.getEndedFollowUps(UUID.randomUUID(), query),
                FollowUpManagementErrorCode.INVALID_DATE_RANGE
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("종료 목록이 비어 있으면 미등록 사유를 조회하지 않는다")
    void skipReasonsWhenEndedRowsEmpty() {
        UUID storeId = UUID.randomUUID();
        FollowUpEndedQuery query = new FollowUpEndedQuery();
        when(queryRepository.findEndedRows(eq(storeId), any(), eq(0), eq(20)))
                .thenReturn(new EndedPageRows(List.of(), 0));

        FollowUpEndedListResponse response = followUpManagementService.getEndedFollowUps(storeId, query);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        verify(queryRepository, never()).findNonConversionReasons(anyCollection());
    }

    private void assertFollowUpManagementError(Runnable action, FollowUpManagementErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }

    private BoardRow boardRow(
            UUID followUpId,
            UUID customerId,
            int contactRound,
            String customerName,
            Integer priorityScore,
            UUID messageTemplateId
    ) {
        return new BoardRow(
                followUpId,
                customerId,
                customerName,
                "010-1234-5678",
                "FEMALE",
                "PT",
                "PENDING",
                "PENDING",
                contactRound,
                LocalDate.of(2026, 10, 15),
                "후속 연락 메모",
                false,
                null,
                "WARM",
                priorityScore,
                "부담 적은 시작 옵션 제안",
                "큰 패키지보다 시작 부담이 낮은 옵션을 안내합니다.",
                messageTemplateId,
                "DRAFT",
                OffsetDateTime.parse("2026-10-15T10:00:00+09:00")
        );
    }

    private EndedRow endedRow(
            UUID followUpId,
            UUID customerId,
            String followUpStatus,
            boolean followUpCompleted
    ) {
        return new EndedRow(
                followUpId,
                customerId,
                "김민지",
                "010-1234-5678",
                "FEMALE",
                "PT",
                "PENDING",
                followUpStatus,
                3,
                true,
                OffsetDateTime.parse("2026-10-15T15:30:00+09:00"),
                UUID.randomUUID(),
                "SENT",
                OffsetDateTime.parse("2026-10-15T13:20:00+09:00"),
                "3차 연락 완료",
                followUpCompleted
        );
    }
}
