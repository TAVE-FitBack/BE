package com.fitback.domain.customer.service;

import com.fitback.domain.customer.dto.request.ConsultationListQuery;
import com.fitback.domain.customer.dto.request.InquiryListQuery;
import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementInquiryListResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.exception.CustomerManagementErrorCode;
import com.fitback.domain.store.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationPageRows;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationSearchCondition;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.FollowUpStageRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InflowPathCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InquiryPageRows;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InquiryRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InquirySearchCondition;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.NonConversionReasonRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ServiceCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.SummaryCounts;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator.MonthRange;
import com.fitback.domain.customer.support.FollowUpManagementStageAssembler;
import com.fitback.domain.inquiry.client.AiInquiryClient;
import com.fitback.domain.user.repository.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerManagementServiceTest {

    @Mock
    private CustomerManagementQueryRepository queryRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private InflowPathOptionRepository inflowPathOptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FollowUpManagementStageAssembler followUpManagementStageAssembler;
    @Mock
    private AiInquiryClient aiInquiryClient;

    @InjectMocks
    private CustomerManagementService customerManagementService;

    @Test
    @DisplayName("선택 월의 상담고객관리 상단 통계와 비율을 반환한다")
    void getSummary() {
        UUID storeId = UUID.randomUUID();
        UUID ptId = UUID.randomUUID();
        UUID spinningId = UUID.randomUUID();
        UUID walkInId = UUID.randomUUID();
        SummaryCounts counts = new SummaryCounts(81, 47, 34, 55, 33);

        when(queryRepository.findSummaryCounts(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(counts);
        when(queryRepository.findServiceCounts(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        new ServiceCountRow(ptId, "PT", 44),
                        new ServiceCountRow(spinningId, "스피닝", 26)
                ));
        when(queryRepository.findInflowPathCounts(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new InflowPathCountRow(walkInId, "워크인", 40)));

        CustomerManagementSummaryResponse response =
                customerManagementService.getSummary(storeId, "2026-10");

        assertThat(response.getMonth()).isEqualTo("2026-10");
        assertThat(response.getRegistrationRate()).isEqualTo(60);
        assertThat(response.getNewConsultationCount()).isEqualTo(81);
        assertThat(response.getNewRegistrationCount()).isEqualTo(47);
        assertThat(response.getNonRegisteredCount()).isEqualTo(34);
        assertThat(response.getServiceConsultationRates()).hasSize(2);
        assertThat(response.getServiceConsultationRates().get(0).getServiceId()).isEqualTo(ptId);
        assertThat(response.getServiceConsultationRates().get(0).getCount()).isEqualTo(44);
        assertThat(response.getServiceConsultationRates().get(0).getRate()).isEqualTo(80);
        assertThat(response.getServiceConsultationRates().get(1).getRate()).isEqualTo(47);
        assertThat(response.getInflowPathRates()).singleElement().satisfies(rate -> {
            assertThat(rate.getInflowPathId()).isEqualTo(walkInId);
            assertThat(rate.getCount()).isEqualTo(40);
            assertThat(rate.getRate()).isEqualTo(73);
        });

        ArgumentCaptor<MonthRange> rangeCaptor = ArgumentCaptor.forClass(MonthRange.class);
        verify(queryRepository).findSummaryCounts(org.mockito.ArgumentMatchers.eq(storeId), rangeCaptor.capture());
        assertThat(rangeCaptor.getValue().startInclusive().toString()).isEqualTo("2026-10-01T00:00+09:00");
        assertThat(rangeCaptor.getValue().endExclusive().toString()).isEqualTo("2026-11-01T00:00+09:00");
    }

    @Test
    @DisplayName("통계 데이터가 없으면 0과 빈 목록을 반환한다")
    void getEmptySummary() {
        UUID storeId = UUID.randomUUID();
        SummaryCounts emptyCounts = new SummaryCounts(0, 0, 0, 0, 0);

        when(queryRepository.findSummaryCounts(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(emptyCounts);
        when(queryRepository.findServiceCounts(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(queryRepository.findInflowPathCounts(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        CustomerManagementSummaryResponse response =
                customerManagementService.getSummary(storeId, "2026-10");

        assertThat(response.getRegistrationRate()).isZero();
        assertThat(response.getNewConsultationCount()).isZero();
        assertThat(response.getNewRegistrationCount()).isZero();
        assertThat(response.getNonRegisteredCount()).isZero();
        assertThat(response.getServiceConsultationRates()).isEmpty();
        assertThat(response.getInflowPathRates()).isEmpty();
    }

    @Test
    @DisplayName("로그인 사용자에게 storeId가 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void rejectMissingStoreId() {
        assertThatThrownBy(() -> customerManagementService.getSummary(null, "2026-10"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerManagementErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("상담 목록은 최신 상담과 미등록 사유를 고객 단위 페이지로 조합한다")
    void getConsultations() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        ConsultationListQuery query = new ConsultationListQuery();
        query.setMonth("2026-10");
        query.setKeyword(" 김민지 ");
        query.setGender("female");
        query.setStatus("pending");
        query.setPage(0);
        query.setSize(10);
        ConsultationRow row = new ConsultationRow(
                customerId,
                "김민지",
                "010-1234-5678",
                "FEMALE",
                LocalDate.of(2001, 5, 10),
                serviceId,
                "스피닝",
                inflowPathId,
                "네이버 예약",
                "CONSULTATION",
                "최신 상담 요약",
                "WARM",
                "PENDING",
                OffsetDateTime.parse("2026-10-12T14:00:00+09:00"),
                counselorId,
                "이담당"
        );
        when(queryRepository.findConsultations(
                org.mockito.ArgumentMatchers.eq(storeId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10)
        )).thenReturn(new ConsultationPageRows(List.of(row), 11));
        when(queryRepository.findNonConversionReasons(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(
                        new NonConversionReasonRow(customerId, "PRICE_BURDEN"),
                        new NonConversionReasonRow(customerId, "SCHEDULE_CONFLICT")
                ));
        FollowUpStageRow activeFollowUp = new FollowUpStageRow(
                customerId,
                "PENDING",
                2,
                OffsetDateTime.parse("2026-10-13T10:00:00+09:00"),
                OffsetDateTime.parse("2026-10-13T10:00:00+09:00")
        );
        CustomerManagementConsultationListResponse.FollowUpManagementStageResponse followUpStage =
                CustomerManagementConsultationListResponse.FollowUpManagementStageResponse.builder()
                        .type(CustomerManagementConsultationListResponse.FollowUpManagementStageType.ROUND)
                        .contactRound(2)
                        .label("2차 연락 대상")
                        .build();
        when(queryRepository.findPendingFollowUps(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(activeFollowUp));
        when(queryRepository.findLatestFollowUps(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());
        when(followUpManagementStageAssembler.assemble(
                org.mockito.ArgumentMatchers.eq(activeFollowUp),
                org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(followUpStage);

        CustomerManagementConsultationListResponse response =
                customerManagementService.getConsultations(storeId, query);

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(11);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getCustomerId()).isEqualTo(customerId);
            assertThat(item.getLatestMemo()).isEqualTo("최신 상담 요약");
            assertThat(item.getManagementStage().name()).isEqualTo("CONSULTATION");
            assertThat(item.getFollowUpManagementStage()).isSameAs(followUpStage);
            assertThat(item.getLeadTemperature()).isEqualTo("WARM");
            assertThat(item.getCounselorName()).isEqualTo("이담당");
            assertThat(item.getNonConversionReasons())
                    .extracting(CustomerManagementConsultationListResponse.NonConversionReasonInfo::getDisplayName)
                    .containsExactly("이용료 부담", "일정 문제");
        });

        ArgumentCaptor<ConsultationSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(ConsultationSearchCondition.class);
        verify(queryRepository).findConsultations(
                org.mockito.ArgumentMatchers.eq(storeId),
                org.mockito.ArgumentMatchers.any(),
                conditionCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10)
        );
        assertThat(conditionCaptor.getValue().keyword()).isEqualTo("김민지");
        assertThat(conditionCaptor.getValue().gender()).isEqualTo("FEMALE");
        assertThat(conditionCaptor.getValue().status()).isEqualTo("PENDING");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("consultation list maps follow_up stages from batched active and latest rows")
    void getConsultationsWithFollowUpManagementStages() {
        UUID storeId = UUID.randomUUID();
        UUID roundOneCustomerId = UUID.randomUUID();
        UUID roundTwoCustomerId = UUID.randomUUID();
        UUID roundThreeCustomerId = UUID.randomUUID();
        UUID completedCustomerId = UUID.randomUUID();
        UUID closedCustomerId = UUID.randomUUID();
        UUID noneCustomerId = UUID.randomUUID();
        UUID otherStoreCustomerId = UUID.randomUUID();
        ConsultationListQuery query = new ConsultationListQuery();
        query.setMonth("2026-10");
        query.setPage(0);
        query.setSize(10);

        List<ConsultationRow> rows = List.of(
                consultationRow(roundOneCustomerId, "Round One"),
                consultationRow(roundTwoCustomerId, "Round Two"),
                consultationRow(roundThreeCustomerId, "Round Three"),
                consultationRow(completedCustomerId, "Completed"),
                consultationRow(closedCustomerId, "Closed"),
                consultationRow(noneCustomerId, "None")
        );
        when(queryRepository.findConsultations(
                org.mockito.ArgumentMatchers.eq(storeId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10)
        )).thenReturn(new ConsultationPageRows(rows, rows.size()));
        when(queryRepository.findNonConversionReasons(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());

        FollowUpStageRow roundOne = followUp(roundOneCustomerId, "PENDING", 1);
        FollowUpStageRow roundTwo = followUp(roundTwoCustomerId, "SENT", 2);
        FollowUpStageRow roundThree = followUp(roundThreeCustomerId, "PENDING", 3);
        FollowUpStageRow completed = followUp(completedCustomerId, "COMPLETED", 3);
        FollowUpStageRow closed = followUp(closedCustomerId, "CLOSED", 2);
        when(queryRepository.findPendingFollowUps(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(roundOne, roundTwo, roundThree));
        when(queryRepository.findLatestFollowUps(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(completed, closed));

        CustomerManagementConsultationListResponse.FollowUpManagementStageResponse roundOneStage =
                followUpStage(CustomerManagementConsultationListResponse.FollowUpManagementStageType.ROUND, 1, "1st");
        CustomerManagementConsultationListResponse.FollowUpManagementStageResponse roundTwoStage =
                followUpStage(CustomerManagementConsultationListResponse.FollowUpManagementStageType.ROUND, 2, "2nd");
        CustomerManagementConsultationListResponse.FollowUpManagementStageResponse roundThreeStage =
                followUpStage(CustomerManagementConsultationListResponse.FollowUpManagementStageType.ROUND, 3, "3rd");
        CustomerManagementConsultationListResponse.FollowUpManagementStageResponse completedStage =
                followUpStage(CustomerManagementConsultationListResponse.FollowUpManagementStageType.COMPLETED, 3, "completed");
        CustomerManagementConsultationListResponse.FollowUpManagementStageResponse closedStage =
                followUpStage(CustomerManagementConsultationListResponse.FollowUpManagementStageType.CLOSED, 2, "closed");
        CustomerManagementConsultationListResponse.FollowUpManagementStageResponse noneStage =
                followUpStage(CustomerManagementConsultationListResponse.FollowUpManagementStageType.NONE, null, null);
        when(followUpManagementStageAssembler.assemble(org.mockito.ArgumentMatchers.eq(roundOne),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(roundOneStage);
        when(followUpManagementStageAssembler.assemble(org.mockito.ArgumentMatchers.eq(roundTwo),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(roundTwoStage);
        when(followUpManagementStageAssembler.assemble(org.mockito.ArgumentMatchers.eq(roundThree),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(roundThreeStage);
        when(followUpManagementStageAssembler.assemble(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(completed))).thenReturn(completedStage);
        when(followUpManagementStageAssembler.assemble(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(closed))).thenReturn(closedStage);
        when(followUpManagementStageAssembler.assemble(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(noneStage);

        CustomerManagementConsultationListResponse response =
                customerManagementService.getConsultations(storeId, query);

        assertThat(response.getContent()).hasSize(6);
        assertThat(response.getContent())
                .extracting(item -> item.getFollowUpManagementStage().getType())
                .containsExactly(
                        CustomerManagementConsultationListResponse.FollowUpManagementStageType.ROUND,
                        CustomerManagementConsultationListResponse.FollowUpManagementStageType.ROUND,
                        CustomerManagementConsultationListResponse.FollowUpManagementStageType.ROUND,
                        CustomerManagementConsultationListResponse.FollowUpManagementStageType.COMPLETED,
                        CustomerManagementConsultationListResponse.FollowUpManagementStageType.CLOSED,
                        CustomerManagementConsultationListResponse.FollowUpManagementStageType.NONE
                );
        assertThat(response.getContent())
                .extracting(item -> item.getFollowUpManagementStage().getContactRound())
                .containsExactly(1, 2, 3, 3, 2, null);

        ArgumentCaptor<java.util.Collection<UUID>> activeCustomerIdsCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<java.util.Collection<UUID>> latestCustomerIdsCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(queryRepository).findPendingFollowUps(activeCustomerIdsCaptor.capture());
        verify(queryRepository).findLatestFollowUps(latestCustomerIdsCaptor.capture());
        assertThat(activeCustomerIdsCaptor.getValue())
                .containsExactlyInAnyOrder(
                        roundOneCustomerId,
                        roundTwoCustomerId,
                        roundThreeCustomerId,
                        completedCustomerId,
                        closedCustomerId,
                        noneCustomerId
                )
                .doesNotContain(otherStoreCustomerId);
        assertThat(latestCustomerIdsCaptor.getValue())
                .containsExactlyInAnyOrderElementsOf(activeCustomerIdsCaptor.getValue());
    }

    @Test
    @DisplayName("consultation list passes managementStage filter to repository condition")
    void getConsultationsWithManagementStageFilter() {
        for (String filter : List.of("ROUND_1", "ROUND_2", "ROUND_3", "COMPLETED", "CLOSED", "NONE")) {
            UUID storeId = UUID.randomUUID();
            ConsultationListQuery query = new ConsultationListQuery();
            query.setMonth("2026-10");
            query.setManagementStage(filter.toLowerCase(java.util.Locale.ROOT));
            query.setPage(0);
            query.setSize(10);
            when(queryRepository.findConsultations(
                    org.mockito.ArgumentMatchers.eq(storeId),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(0),
                    org.mockito.ArgumentMatchers.eq(10)
            )).thenReturn(new ConsultationPageRows(List.of(), 0));

            customerManagementService.getConsultations(storeId, query);

            ArgumentCaptor<ConsultationSearchCondition> conditionCaptor =
                    ArgumentCaptor.forClass(ConsultationSearchCondition.class);
            verify(queryRepository).findConsultations(
                    org.mockito.ArgumentMatchers.eq(storeId),
                    org.mockito.ArgumentMatchers.any(),
                    conditionCaptor.capture(),
                    org.mockito.ArgumentMatchers.eq(0),
                    org.mockito.ArgumentMatchers.eq(10)
            );
            assertThat(conditionCaptor.getValue().managementStage()).isEqualTo(filter);
            org.mockito.Mockito.reset(queryRepository);
        }
    }

    @Test
    @DisplayName("invalid managementStage filter is rejected")
    void rejectInvalidManagementStageFilter() {
        ConsultationListQuery query = new ConsultationListQuery();
        query.setManagementStage("ROUND");

        assertManagementError(
                () -> customerManagementService.getConsultations(UUID.randomUUID(), query),
                CustomerManagementErrorCode.INVALID_MANAGEMENT_STAGE
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("상담 목록이 비어 있으면 미등록 사유를 조회하지 않고 빈 페이지를 반환한다")
    void getEmptyConsultations() {
        UUID storeId = UUID.randomUUID();
        ConsultationListQuery query = new ConsultationListQuery();
        query.setMonth("2026-10");
        when(queryRepository.findConsultations(
                org.mockito.ArgumentMatchers.eq(storeId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10)
        )).thenReturn(new ConsultationPageRows(List.of(), 0));

        CustomerManagementConsultationListResponse response =
                customerManagementService.getConsultations(storeId, query);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isHasNext()).isFalse();
        verify(queryRepository, org.mockito.Mockito.never())
                .findNonConversionReasons(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    @DisplayName("잘못된 상담 목록 필터는 INVALID_FILTER_CONDITION 예외가 발생한다")
    void rejectInvalidConsultationFilter() {
        ConsultationListQuery query = new ConsultationListQuery();
        query.setGender("UNKNOWN");

        assertThatThrownBy(() -> customerManagementService.getConsultations(UUID.randomUUID(), query))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("문의 목록은 문의 한 건당 한 행과 100자 원문 미리보기를 반환한다")
    void getInquiries() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        InquiryListQuery query = new InquiryListQuery();
        query.setMonth("2026-10");
        query.setKeyword(" 김문의 ");
        query.setGender("female");
        query.setInquiryStatus("visit_scheduled");
        query.setPage(0);
        query.setSize(10);
        InquiryRow row = new InquiryRow(
                inquiryId,
                "김문의",
                "010-1111-2222",
                "FEMALE",
                LocalDate.of(2000, 2, 3),
                serviceId,
                "PT",
                inflowPathId,
                "워크인",
                "가".repeat(101),
                "VISIT_SCHEDULED",
                OffsetDateTime.parse("2026-10-12T11:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T15:00:00+09:00"),
                counselorId,
                "이담당",
                null,
                null
        );
        when(queryRepository.findInquiries(
                org.mockito.ArgumentMatchers.eq(storeId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10)
        )).thenReturn(new InquiryPageRows(List.of(row), 11));

        CustomerManagementInquiryListResponse response =
                customerManagementService.getInquiries(storeId, query);

        assertThat(response.getTotalElements()).isEqualTo(11);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getInquiryId()).isEqualTo(inquiryId);
            assertThat(item.getMemo()).isEqualTo("가".repeat(100) + "…");
            assertThat(item.getInquiryStatus().name()).isEqualTo("VISIT_SCHEDULED");
            assertThat(item.getInquiryStatusName()).isEqualTo("방문 예정");
            assertThat(item.isConverted()).isFalse();
            assertThat(item.getConvertedCustomerId()).isNull();
            assertThat(item.getConvertedConsultationId()).isNull();
        });

        ArgumentCaptor<InquirySearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(InquirySearchCondition.class);
        verify(queryRepository).findInquiries(
                org.mockito.ArgumentMatchers.eq(storeId),
                org.mockito.ArgumentMatchers.any(),
                conditionCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10)
        );
        assertThat(conditionCaptor.getValue().keyword()).isEqualTo("김문의");
        assertThat(conditionCaptor.getValue().gender()).isEqualTo("FEMALE");
        assertThat(conditionCaptor.getValue().inquiryStatus()).isEqualTo("VISIT_SCHEDULED");
        verifyNoInteractions(aiInquiryClient);
    }

    @Test
    @DisplayName("CONVERTED 문의 상태는 목록 필터로 허용하지 않는다")
    void rejectConvertedInquiryFilter() {
        InquiryListQuery query = new InquiryListQuery();
        query.setInquiryStatus("CONVERTED");

        assertThatThrownBy(() -> customerManagementService.getInquiries(UUID.randomUUID(), query))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);

        verifyNoInteractions(queryRepository, aiInquiryClient);
    }

    @Test
    @DisplayName("문의 목록 필터 ID는 로그인 사용자의 매장 범위에서 검증한다")
    void validateInquiryFilterReferences() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        InquiryListQuery query = new InquiryListQuery();
        query.setServiceId(serviceId);
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerManagementService.getInquiries(storeId, query))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);

        verifyNoInteractions(queryRepository, aiInquiryClient);
    }

    @Test
    @DisplayName("상담과 문의 목록은 storeId가 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void rejectMissingStoreIdForLists() {
        assertManagementError(
                () -> customerManagementService.getConsultations(null, new ConsultationListQuery()),
                CustomerManagementErrorCode.STORE_NOT_ASSIGNED
        );
        assertManagementError(
                () -> customerManagementService.getInquiries(null, new InquiryListQuery()),
                CustomerManagementErrorCode.STORE_NOT_ASSIGNED
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("잘못된 월 형식은 서비스 진입점에서 INVALID_MONTH_FORMAT 예외가 발생한다")
    void rejectInvalidMonthAtServiceBoundary() {
        ConsultationListQuery query = new ConsultationListQuery();
        query.setMonth("2026-13");

        assertManagementError(
                () -> customerManagementService.getConsultations(UUID.randomUUID(), query),
                CustomerManagementErrorCode.INVALID_MONTH_FORMAT
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("100자를 초과한 검색어는 INVALID_SEARCH_CONDITION 예외가 발생한다")
    void rejectInvalidSearchConditionAtServiceBoundary() {
        InquiryListQuery query = new InquiryListQuery();
        query.setKeyword("가".repeat(101));

        assertManagementError(
                () -> customerManagementService.getInquiries(UUID.randomUUID(), query),
                CustomerManagementErrorCode.INVALID_SEARCH_CONDITION
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("허용 범위를 벗어난 페이지는 INVALID_PAGE_REQUEST 예외가 발생한다")
    void rejectInvalidPageAtServiceBoundary() {
        ConsultationListQuery query = new ConsultationListQuery();
        query.setPage(-1);

        assertManagementError(
                () -> customerManagementService.getConsultations(UUID.randomUUID(), query),
                CustomerManagementErrorCode.INVALID_PAGE_REQUEST
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("타 매장 서비스 필터는 INVALID_FILTER_CONDITION 예외가 발생한다")
    void rejectOutOfStoreServiceFilter() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ConsultationListQuery query = new ConsultationListQuery();
        query.setServiceId(serviceId);
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertManagementError(
                () -> customerManagementService.getConsultations(storeId, query),
                CustomerManagementErrorCode.INVALID_FILTER_CONDITION
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("타 매장 방문경로 필터는 INVALID_FILTER_CONDITION 예외가 발생한다")
    void rejectOutOfStoreInflowPathFilter() {
        UUID storeId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        ConsultationListQuery query = new ConsultationListQuery();
        query.setInflowPathId(inflowPathId);
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.empty());

        assertManagementError(
                () -> customerManagementService.getConsultations(storeId, query),
                CustomerManagementErrorCode.INVALID_FILTER_CONDITION
        );

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("타 매장 담당자 필터는 INVALID_FILTER_CONDITION 예외가 발생한다")
    void rejectOutOfStoreCounselorFilter() {
        UUID storeId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        InquiryListQuery query = new InquiryListQuery();
        query.setCounselorId(counselorId);
        when(userRepository.findByIdAndStore_Id(counselorId, storeId))
                .thenReturn(Optional.empty());

        assertManagementError(
                () -> customerManagementService.getInquiries(storeId, query),
                CustomerManagementErrorCode.INVALID_FILTER_CONDITION
        );

        verifyNoInteractions(queryRepository, aiInquiryClient);
    }

    private ConsultationRow consultationRow(UUID customerId, String name) {
        return new ConsultationRow(
                customerId,
                name,
                "010-0000-0000",
                "FEMALE",
                LocalDate.of(2000, 1, 1),
                UUID.randomUUID(),
                "PT",
                UUID.randomUUID(),
                "Walk in",
                "CONSULTATION",
                "Latest memo",
                "WARM",
                "PENDING",
                OffsetDateTime.parse("2026-10-12T14:00:00+09:00"),
                UUID.randomUUID(),
                "Counselor"
        );
    }

    private FollowUpStageRow followUp(UUID customerId, String status, int contactRound) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-13T10:00:00+09:00");
        return new FollowUpStageRow(customerId, status, contactRound, now, now);
    }

    private CustomerManagementConsultationListResponse.FollowUpManagementStageResponse followUpStage(
            CustomerManagementConsultationListResponse.FollowUpManagementStageType type,
            Integer contactRound,
            String label
    ) {
        return CustomerManagementConsultationListResponse.FollowUpManagementStageResponse.builder()
                .type(type)
                .contactRound(contactRound)
                .label(label)
                .build();
    }

    private void assertManagementError(Runnable action, CustomerManagementErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }
}
