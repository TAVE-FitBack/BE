package com.fitback.domain.analysisreport.service;

import com.fitback.domain.analysisreport.dto.response.AnalysisReportFollowUpResponse;
import com.fitback.domain.analysisreport.exception.AnalysisReportErrorCode;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository.ConversionGraphCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository.ConversionRoundCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository.NonConversionReasonCount;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository.RegistrationChangeCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository.SummaryCounts;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisReportFollowUpServiceTest {

    @Mock
    private AnalysisReportFollowUpQueryRepository queryRepository;

    @InjectMocks
    private AnalysisReportFollowUpService service;

    @Test
    @DisplayName("후속관리 리포트는 집계 결과를 명세 응답 구조로 조립한다")
    void getFollowUpReport() {
        UUID storeId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(18, 7, 11, 8));
        when(queryRepository.findRegistrationChangeCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationChangeCounts(20, 7, 12));
        when(queryRepository.findConversionGraphCounts(eq(storeId), any(), any()))
                .thenReturn(new ConversionGraphCounts(18, 8, 10, 2));
        when(queryRepository.findConversionRoundCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new ConversionRoundCounts(1, 4, 12, 3),
                        new ConversionRoundCounts(2, 3, 8, 2),
                        new ConversionRoundCounts(3, 1, 4, 1)
                ));
        when(queryRepository.findNonRegisteredTargetCount(eq(storeId), any(), any()))
                .thenReturn(10L);
        when(queryRepository.findNonConversionReasonCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new NonConversionReasonCount("PRICE_BURDEN", 8),
                        new NonConversionReasonCount("SCHEDULE_CONFLICT", 2)
                ));

        AnalysisReportFollowUpResponse response = service.getFollowUpReport(storeId, "2026-10");

        assertThat(response.getMonth()).isEqualTo("2026-10");
        assertThat(response.getSummary().getTargetCustomerCount()).isEqualTo(18);
        assertThat(response.getSummary().getPendingFollowUpCount()).isEqualTo(7);
        assertThat(response.getSummary().getCompletedFollowUpCount()).isEqualTo(11);
        assertThat(response.getSummary().getRegisteredAfterFollowUpCount()).isEqualTo(8);
        assertThat(response.getSummary().getFollowUpRegistrationConversionRate()).isEqualTo(44);
        assertThat(response.getRegistrationChange().getBeforeRate()).isEqualTo(35);
        assertThat(response.getRegistrationChange().getCurrentRate()).isEqualTo(60);
        assertThat(response.getRegistrationChange().getChangePoint()).isEqualTo(25);
        assertThat(response.getConversionGraph().getInitialNonRegisteredCount()).isEqualTo(18);
        assertThat(response.getConversionGraph().getFinalRegisteredCount()).isEqualTo(8);
        assertThat(response.getConversionGraph().getNonRegisteredOrLostCount()).isEqualTo(10);
        assertThat(response.getConversionGraph().getUnattributedRegisteredCount()).isEqualTo(2);
        assertThat(response.getConversionGraph().getRounds()).extracting("contactRound")
                .containsExactly(1, 2, 3);
        assertThat(response.getNonConversionReasons()).hasSize(2);
        assertThat(response.getNonConversionReasons().get(0).getDisplayName()).isEqualTo("가격 부담");
        assertThat(response.getNonConversionReasons().get(0).getRate()).isEqualTo(80);
        assertThat(response.getAiRecommendations()).isNotEmpty();

        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(queryRepository).findSummaryCounts(eq(storeId), startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"));
        assertThat(endCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-11-01T00:00:00+09:00"));
    }

    @Test
    @DisplayName("storeId가 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void getFollowUpReportStoreNotAssigned() {
        assertThatThrownBy(() -> service.getFollowUpReport(null, "2026-10"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnalysisReportErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("month 형식이 YYYY-MM이 아니면 INVALID_MONTH_FORMAT 예외가 발생한다")
    void getFollowUpReportInvalidMonth() {
        UUID storeId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getFollowUpReport(storeId, "2026-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnalysisReportErrorCode.INVALID_MONTH_FORMAT);

        verifyNoInteractions(queryRepository);
    }
}
