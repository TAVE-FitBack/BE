package com.fitback.domain.analysisreport.service;

import com.fitback.domain.analysisreport.dto.response.AnalysisReportTotalResponse;
import com.fitback.domain.analysisreport.exception.AnalysisReportErrorCode;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.InflowPathCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.MonthlyRegistrationCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.MonthlyServiceRegistrationCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.RegistrationCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.ServiceConsultationCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.SummaryCounts;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisReportTotalServiceTest {

    @Mock
    private AnalysisReportTotalQueryRepository queryRepository;

    @InjectMocks
    private AnalysisReportTotalService service;

    @Test
    @DisplayName("전체 리포트는 집계 결과를 명세 응답 구조로 조립한다")
    void getTotalReport() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(81, 47, 34));
        when(queryRepository.findServiceConsultationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new ServiceConsultationCounts(serviceId, "PT", 21)));
        when(queryRepository.findMonthlyRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new MonthlyRegistrationCounts("2026-05", 42, 22),
                        new MonthlyRegistrationCounts("2026-06", 50, 25),
                        new MonthlyRegistrationCounts("2026-07", 40, 16),
                        new MonthlyRegistrationCounts("2026-08", 60, 30),
                        new MonthlyRegistrationCounts("2026-09", 30, 14),
                        new MonthlyRegistrationCounts("2026-10", 81, 47)
                ));
        when(queryRepository.findMonthlyServiceRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new MonthlyServiceRegistrationCounts("2026-05", serviceId, "PT", 10, 6),
                        new MonthlyServiceRegistrationCounts("2026-06", serviceId, "PT", 12, 7),
                        new MonthlyServiceRegistrationCounts("2026-07", serviceId, "PT", 0, 0),
                        new MonthlyServiceRegistrationCounts("2026-08", serviceId, "PT", 8, 3),
                        new MonthlyServiceRegistrationCounts("2026-09", serviceId, "PT", 9, 4),
                        new MonthlyServiceRegistrationCounts("2026-10", serviceId, "PT", 21, 15)
                ));
        when(queryRepository.findRegistrationCounts(
                eq(storeId),
                eq(LocalDate.of(2026, 9, 1)),
                eq(LocalDate.of(2026, 10, 1))
        )).thenReturn(new RegistrationCounts(30, 14));
        when(queryRepository.findInflowPathCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new InflowPathCounts(inflowPathId, "워크인", 40, 30)));

        AnalysisReportTotalResponse response = service.getTotalReport(storeId, "2026-10");

        assertThat(response.getMonth()).isEqualTo("2026-10");
        assertThat(response.getSummary().getNewRegistrationRate()).isEqualTo(58);
        assertThat(response.getSummary().getNewConsultationCount()).isEqualTo(81);
        assertThat(response.getSummary().getNewRegistrationCount()).isEqualTo(47);
        assertThat(response.getSummary().getNonRegisteredCount()).isEqualTo(34);
        assertThat(response.getServiceConsultations()).singleElement().satisfies(item -> {
            assertThat(item.getServiceId()).isEqualTo(serviceId);
            assertThat(item.getServiceName()).isEqualTo("PT");
            assertThat(item.getConsultedCustomerCount()).isEqualTo(21);
            assertThat(item.getTotalNewConsultationCount()).isEqualTo(81);
            assertThat(item.getConsultationRate()).isEqualTo(26);
        });
        assertThat(response.getRegistrationTrend().getMonths())
                .containsExactly("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(response.getRegistrationTrend().getSelectedMonthRate()).isEqualTo(58);
        assertThat(response.getRegistrationTrend().getPreviousMonthRate()).isEqualTo(47);
        assertThat(response.getRegistrationTrend().getMonthOverMonthChangePoint()).isEqualTo(11);
        assertThat(response.getRegistrationTrend().getDirection()).isEqualTo("UP");
        assertThat(response.getRegistrationTrend().getSeries()).hasSize(2);
        assertThat(response.getRegistrationTrend().getSeries().get(0).getKey()).isEqualTo("ALL");
        assertThat(response.getRegistrationTrend().getSeries().get(1).getKey()).isEqualTo(serviceId.toString());
        assertThat(response.getRegistrationTrend().getSeries().get(1).getPoints())
                .extracting("month", "rate", "consultedCount", "registeredCount")
                .contains(tuple("2026-07", 0, 0L, 0L));
        assertThat(response.getInflowPaths()).singleElement().satisfies(item -> {
            assertThat(item.getInflowPathId()).isEqualTo(inflowPathId);
            assertThat(item.getInflowPathName()).isEqualTo("워크인");
            assertThat(item.getNewConsultationCompositionRate()).isEqualTo(49);
            assertThat(item.getRegisteredCompositionRate()).isEqualTo(64);
            assertThat(item.getNewConsultationCount()).isEqualTo(40);
            assertThat(item.getRegisteredCount()).isEqualTo(30);
        });

        ArgumentCaptor<LocalDate> trendStartCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> trendEndCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(queryRepository).findMonthlyRegistrationCounts(
                eq(storeId),
                trendStartCaptor.capture(),
                trendEndCaptor.capture()
        );
        assertThat(trendStartCaptor.getValue()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(trendEndCaptor.getValue()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("빈 데이터는 수치와 비율을 0으로 조립한다")
    void getTotalReportEmptyData() {
        UUID storeId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(0, 0, 0));
        when(queryRepository.findServiceConsultationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(queryRepository.findMonthlyRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new MonthlyRegistrationCounts("2026-05", 0, 0),
                        new MonthlyRegistrationCounts("2026-06", 0, 0),
                        new MonthlyRegistrationCounts("2026-07", 0, 0),
                        new MonthlyRegistrationCounts("2026-08", 0, 0),
                        new MonthlyRegistrationCounts("2026-09", 0, 0),
                        new MonthlyRegistrationCounts("2026-10", 0, 0)
                ));
        when(queryRepository.findMonthlyServiceRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(queryRepository.findRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationCounts(0, 0));
        when(queryRepository.findInflowPathCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());

        AnalysisReportTotalResponse response = service.getTotalReport(storeId, "2026-10");

        assertThat(response.getSummary().getNewRegistrationRate()).isZero();
        assertThat(response.getSummary().getNewConsultationCount()).isZero();
        assertThat(response.getServiceConsultations()).isEmpty();
        assertThat(response.getRegistrationTrend().getSelectedMonthRate()).isZero();
        assertThat(response.getRegistrationTrend().getPreviousMonthRate()).isZero();
        assertThat(response.getRegistrationTrend().getMonthOverMonthChangePoint()).isZero();
        assertThat(response.getRegistrationTrend().getDirection()).isEqualTo("FLAT");
        assertThat(response.getRegistrationTrend().getSeries()).singleElement()
                .satisfies(series -> assertThat(series.getPoints())
                        .extracting("rate")
                        .containsOnly(0));
        assertThat(response.getInflowPaths()).isEmpty();
    }

    @Test
    @DisplayName("분모가 0이면 구성비와 전환율을 0으로 계산한다")
    void getTotalReportZeroDenominator() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(0, 0, 0));
        when(queryRepository.findServiceConsultationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new ServiceConsultationCounts(serviceId, "PT", 5)));
        when(queryRepository.findMonthlyRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new MonthlyRegistrationCounts("2026-10", 0, 3)));
        when(queryRepository.findMonthlyServiceRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new MonthlyServiceRegistrationCounts("2026-10", serviceId, "PT", 0, 2)));
        when(queryRepository.findRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationCounts(0, 1));
        when(queryRepository.findInflowPathCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new InflowPathCounts(inflowPathId, "워크인", 4, 2)));

        AnalysisReportTotalResponse response = service.getTotalReport(storeId, "2026-10");

        assertThat(response.getSummary().getNewRegistrationRate()).isZero();
        assertThat(response.getServiceConsultations()).singleElement()
                .extracting(AnalysisReportTotalResponse.ServiceConsultation::getConsultationRate)
                .isEqualTo(0);
        assertThat(response.getRegistrationTrend().getSeries().get(0).getPoints()).singleElement()
                .extracting(AnalysisReportTotalResponse.Point::getRate)
                .isEqualTo(0);
        assertThat(response.getRegistrationTrend().getSeries().get(1).getPoints()).singleElement()
                .extracting(AnalysisReportTotalResponse.Point::getRate)
                .isEqualTo(0);
        assertThat(response.getInflowPaths()).singleElement().satisfies(item -> {
            assertThat(item.getNewConsultationCompositionRate()).isZero();
            assertThat(item.getRegisteredCompositionRate()).isZero();
        });
    }

    @Test
    @DisplayName("전월 대비 전환율이 낮으면 direction은 DOWN이다")
    void getTotalReportDirectionDown() {
        UUID storeId = UUID.randomUUID();
        givenEmptyCollections(storeId);
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(10, 3, 7));
        when(queryRepository.findRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationCounts(10, 5));

        AnalysisReportTotalResponse response = service.getTotalReport(storeId, "2026-10");

        assertThat(response.getRegistrationTrend().getMonthOverMonthChangePoint()).isEqualTo(-20);
        assertThat(response.getRegistrationTrend().getDirection()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("전월 대비 전환율이 같으면 direction은 FLAT이다")
    void getTotalReportDirectionFlat() {
        UUID storeId = UUID.randomUUID();
        givenEmptyCollections(storeId);
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(10, 5, 5));
        when(queryRepository.findRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationCounts(20, 10));

        AnalysisReportTotalResponse response = service.getTotalReport(storeId, "2026-10");

        assertThat(response.getRegistrationTrend().getMonthOverMonthChangePoint()).isZero();
        assertThat(response.getRegistrationTrend().getDirection()).isEqualTo("FLAT");
    }

    @Test
    @DisplayName("storeId가 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void getTotalReportStoreNotAssigned() {
        assertThatThrownBy(() -> service.getTotalReport(null, "2026-10"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnalysisReportErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("month 형식이 YYYY-MM이 아니면 INVALID_MONTH_FORMAT 예외가 발생한다")
    void getTotalReportInvalidMonth() {
        UUID storeId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getTotalReport(storeId, "2026-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnalysisReportErrorCode.INVALID_MONTH_FORMAT);

        verifyNoInteractions(queryRepository);
    }

    private void givenEmptyCollections(UUID storeId) {
        when(queryRepository.findServiceConsultationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(queryRepository.findMonthlyRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(queryRepository.findMonthlyServiceRegistrationCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(queryRepository.findInflowPathCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
    }
}
