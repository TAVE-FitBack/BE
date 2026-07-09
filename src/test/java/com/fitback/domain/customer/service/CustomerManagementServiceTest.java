package com.fitback.domain.customer.service;

import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.exception.CustomerManagementErrorCode;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InflowPathCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ServiceCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.SummaryCounts;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator.MonthRange;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
