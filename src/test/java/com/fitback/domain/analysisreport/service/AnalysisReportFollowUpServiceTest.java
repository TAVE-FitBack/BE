package com.fitback.domain.analysisreport.service;

import com.fitback.domain.analysisreport.dto.response.AnalysisReportFollowUpResponse;
import com.fitback.domain.analysisreport.exception.AnalysisReportErrorCode;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository;
import com.fitback.domain.analysisreport.repository.AnalysisReportFollowUpQueryRepository.AiInsightPatternCount;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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
        when(queryRepository.findAiInsightPatternCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new AiInsightPatternCount("PRIMARY_REASON", "가격 부담", 4),
                        new AiInsightPatternCount("CAUTION_NOTE", "가격 압박은 피합니다.", 2)
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
        assertThat(response.getAiRecommendations()).hasSize(3);
        assertThat(response.getAiRecommendations().get(0).getTitle()).isEqualTo("가격 부담을 낮추는 안내를 우선 강화하세요");
        assertThat(response.getAiRecommendations().get(0).getDescription())
                .contains("AI 인사이트에서도 같은 유형의 후속관리 포인트가 반복");

        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(queryRepository).findSummaryCounts(eq(storeId), startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"));
        assertThat(endCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-11-01T00:00:00+09:00"));
    }

    @Test
    @DisplayName("conversion graph uses saved follow_up_conversion contact rounds")
    void getFollowUpReportUsesConversionRoundCounts() {
        UUID storeId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(9, 2, 7, 6));
        when(queryRepository.findRegistrationChangeCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationChangeCounts(9, 1, 6));
        when(queryRepository.findConversionGraphCounts(eq(storeId), any(), any()))
                .thenReturn(new ConversionGraphCounts(9, 6, 3, 2));
        when(queryRepository.findConversionRoundCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new ConversionRoundCounts(1, 3, 8, 5),
                        new ConversionRoundCounts(2, 2, 4, 2),
                        new ConversionRoundCounts(3, 1, 1, 0)
                ));
        when(queryRepository.findNonRegisteredTargetCount(eq(storeId), any(), any()))
                .thenReturn(3L);
        when(queryRepository.findNonConversionReasonCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(queryRepository.findAiInsightPatternCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());

        AnalysisReportFollowUpResponse response = service.getFollowUpReport(storeId, "2026-10");

        assertThat(response.getConversionGraph().getRounds())
                .extracting("contactRound", "registeredCount", "sentCount", "pendingCount")
                .containsExactly(
                        tuple(1, 3L, 8L, 5L),
                        tuple(2, 2L, 4L, 2L),
                        tuple(3, 1L, 1L, 0L)
                );
        assertThat(response.getConversionGraph().getFinalRegisteredCount()).isEqualTo(6);
        assertThat(response.getConversionGraph().getUnattributedRegisteredCount()).isEqualTo(2);
        assertThat(response.getConversionGraph().getRounds()).extracting("registeredCount")
                .containsExactly(3L, 2L, 1L);
        assertThat(response.getConversionGraph().getRounds().stream()
                .mapToLong(AnalysisReportFollowUpResponse.ConversionRound::getRegisteredCount)
                .sum()).isEqualTo(6);
    }

    @Test
    @DisplayName("month가 없으면 Asia/Seoul 기준 현재 월 범위로 조회한다")
    void getFollowUpReportDefaultMonth() {
        UUID storeId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(5, 1, 4, 2));
        when(queryRepository.findRegistrationChangeCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationChangeCounts(5, 1, 2));
        when(queryRepository.findConversionGraphCounts(eq(storeId), any(), any()))
                .thenReturn(new ConversionGraphCounts(5, 2, 3, 0));
        when(queryRepository.findConversionRoundCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new ConversionRoundCounts(1, 2, 3, 1),
                        new ConversionRoundCounts(2, 0, 1, 0),
                        new ConversionRoundCounts(3, 0, 0, 0)
                ));
        when(queryRepository.findNonRegisteredTargetCount(eq(storeId), any(), any()))
                .thenReturn(3L);
        when(queryRepository.findNonConversionReasonCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new NonConversionReasonCount("NO_RESPONSE", 2)));
        when(queryRepository.findAiInsightPatternCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());

        AnalysisReportFollowUpResponse response = service.getFollowUpReport(storeId, null);

        YearMonth expectedMonth = YearMonth.now(ZoneId.of("Asia/Seoul"));
        assertThat(response.getMonth()).isEqualTo(expectedMonth.toString());

        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(queryRepository).findSummaryCounts(eq(storeId), startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(
                expectedMonth.atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime()
        );
        assertThat(endCaptor.getValue()).isEqualTo(
                expectedMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime()
        );

        ArgumentCaptor<LocalDate> startDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(queryRepository).findRegistrationChangeCounts(
                eq(storeId),
                startDateCaptor.capture(),
                endDateCaptor.capture()
        );
        assertThat(startDateCaptor.getValue()).isEqualTo(expectedMonth.atDay(1));
        assertThat(endDateCaptor.getValue()).isEqualTo(expectedMonth.plusMonths(1).atDay(1));
    }

    @Test
    @DisplayName("데이터가 부족해도 AI 개선사항 제안은 2개 이상 반환한다")
    void getFollowUpReportReturnsAtLeastTwoRecommendationsWhenDataIsInsufficient() {
        UUID storeId = UUID.randomUUID();
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(2, 2, 0, 0));
        when(queryRepository.findRegistrationChangeCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationChangeCounts(2, 0, 0));
        when(queryRepository.findConversionGraphCounts(eq(storeId), any(), any()))
                .thenReturn(new ConversionGraphCounts(2, 0, 2, 0));
        when(queryRepository.findConversionRoundCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new ConversionRoundCounts(1, 0, 0, 1),
                        new ConversionRoundCounts(2, 0, 0, 1),
                        new ConversionRoundCounts(3, 0, 0, 0)
                ));
        when(queryRepository.findNonRegisteredTargetCount(eq(storeId), any(), any()))
                .thenReturn(2L);
        when(queryRepository.findNonConversionReasonCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());
        when(queryRepository.findAiInsightPatternCounts(eq(storeId), any(), any()))
                .thenReturn(List.of());

        AnalysisReportFollowUpResponse response = service.getFollowUpReport(storeId, "2026-10");

        assertThat(response.getAiRecommendations()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.getAiRecommendations()).extracting("title")
                .contains(
                        "후속관리 데이터를 먼저 누적하세요",
                        "미등록 사유를 상담 직후에 정리하세요"
                );
    }

    @Test
    @DisplayName("follow_up_ai_insight는 원문을 노출하지 않고 보조 패턴으로만 사용한다")
    void getFollowUpReportUsesFollowUpAiInsightOnlyAsSupportingPattern() {
        UUID storeId = UUID.randomUUID();
        String rawInsightText = "VIP 고객에게 30% 할인 쿠폰을 직접 제안하고 개인 전화번호로 연락하세요";
        when(queryRepository.findSummaryCounts(eq(storeId), any(), any()))
                .thenReturn(new SummaryCounts(8, 3, 5, 2));
        when(queryRepository.findRegistrationChangeCounts(eq(storeId), any(), any()))
                .thenReturn(new RegistrationChangeCounts(8, 1, 2));
        when(queryRepository.findConversionGraphCounts(eq(storeId), any(), any()))
                .thenReturn(new ConversionGraphCounts(8, 2, 6, 1));
        when(queryRepository.findConversionRoundCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new ConversionRoundCounts(1, 1, 5, 2),
                        new ConversionRoundCounts(2, 1, 3, 1),
                        new ConversionRoundCounts(3, 0, 1, 0)
                ));
        when(queryRepository.findNonRegisteredTargetCount(eq(storeId), any(), any()))
                .thenReturn(6L);
        when(queryRepository.findNonConversionReasonCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(new NonConversionReasonCount("PRICE_BURDEN", 4)));
        when(queryRepository.findAiInsightPatternCounts(eq(storeId), any(), any()))
                .thenReturn(List.of(
                        new AiInsightPatternCount("PERSUASION_POINT", "가격 부담 완화", 3),
                        new AiInsightPatternCount("CAUTION_NOTE", rawInsightText, 1)
                ));

        AnalysisReportFollowUpResponse response = service.getFollowUpReport(storeId, "2026-10");

        String joinedRecommendations = response.getAiRecommendations().stream()
                .map(recommendation -> recommendation.getTitle() + " " + recommendation.getDescription())
                .reduce("", (left, right) -> left + " " + right);
        assertThat(joinedRecommendations).contains("AI 인사이트에서도 같은 유형의 후속관리 포인트가 반복");
        assertThat(joinedRecommendations).doesNotContain(rawInsightText);
        assertThat(response.getAiRecommendations()).hasSizeBetween(2, 3);
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
