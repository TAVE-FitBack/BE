package com.fitback.domain.customer.service;

import com.fitback.domain.customer.dto.request.ConsultationListQuery;
import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.exception.CustomerManagementErrorCode;
import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationPageRows;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationSearchCondition;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InflowPathCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.NonConversionReasonRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ServiceCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.SummaryCounts;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator.MonthRange;
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
}
