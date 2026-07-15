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
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisReportFollowUpService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Map<String, String> REASON_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("PRICE_BURDEN", "가격 부담"),
            Map.entry("SCHEDULE_CONFLICT", "일정 불일치"),
            Map.entry("NEED_CONSIDERATION", "고민 필요"),
            Map.entry("NO_RESPONSE", "응답 없음"),
            Map.entry("LOW_INTEREST", "관심 낮음"),
            Map.entry("COMPARING_OPTIONS", "타 옵션 비교"),
            Map.entry("START_TIMING_DELAY", "시작 시점 보류"),
            Map.entry("OTHER", "기타")
    );

    private final AnalysisReportFollowUpQueryRepository queryRepository;

    public AnalysisReportFollowUpResponse getFollowUpReport(UUID storeId, String month) {
        if (storeId == null) {
            throw new BusinessException(AnalysisReportErrorCode.STORE_NOT_ASSIGNED);
        }

        YearMonth yearMonth = parseMonth(month);
        OffsetDateTime monthStart = yearMonth.atDay(1).atStartOfDay(SEOUL_ZONE).toOffsetDateTime();
        OffsetDateTime monthEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay(SEOUL_ZONE).toOffsetDateTime();
        LocalDate monthStartDate = yearMonth.atDay(1);
        LocalDate monthEndDate = yearMonth.plusMonths(1).atDay(1);

        SummaryCounts summaryCounts = queryRepository.findSummaryCounts(storeId, monthStart, monthEnd);
        RegistrationChangeCounts registrationChangeCounts = queryRepository.findRegistrationChangeCounts(
                storeId,
                monthStartDate,
                monthEndDate
        );
        ConversionGraphCounts graphCounts = queryRepository.findConversionGraphCounts(storeId, monthStart, monthEnd);
        List<ConversionRoundCounts> roundCounts = queryRepository.findConversionRoundCounts(storeId, monthStart, monthEnd);
        long nonRegisteredTargetCount = queryRepository.findNonRegisteredTargetCount(storeId, monthStart, monthEnd);
        List<NonConversionReasonCount> reasonCounts = queryRepository.findNonConversionReasonCounts(
                storeId,
                monthStart,
                monthEnd
        );

        AnalysisReportFollowUpResponse.Summary summary = AnalysisReportFollowUpResponse.Summary.builder()
                .targetCustomerCount(summaryCounts.targetCustomerCount())
                .pendingFollowUpCount(summaryCounts.pendingFollowUpCount())
                .completedFollowUpCount(summaryCounts.completedFollowUpCount())
                .registeredAfterFollowUpCount(summaryCounts.registeredAfterFollowUpCount())
                .followUpRegistrationConversionRate(rate(
                        summaryCounts.registeredAfterFollowUpCount(),
                        summaryCounts.targetCustomerCount()
                ))
                .build();

        int beforeRate = rate(
                registrationChangeCounts.registeredBeforeFollowUpCount(),
                registrationChangeCounts.monthlyCustomerCount()
        );
        int currentRate = rate(
                registrationChangeCounts.currentRegisteredCount(),
                registrationChangeCounts.monthlyCustomerCount()
        );

        List<AnalysisReportFollowUpResponse.ConversionRound> rounds = roundCounts.stream()
                .map(row -> AnalysisReportFollowUpResponse.ConversionRound.builder()
                        .contactRound(row.contactRound())
                        .registeredCount(row.registeredCount())
                        .sentCount(row.sentCount())
                        .pendingCount(row.pendingCount())
                        .build())
                .toList();

        List<AnalysisReportFollowUpResponse.NonConversionReasonItem> nonConversionReasons = reasonCounts.stream()
                .map(row -> AnalysisReportFollowUpResponse.NonConversionReasonItem.builder()
                        .reasonType(row.reasonType())
                        .displayName(displayReasonName(row.reasonType()))
                        .count(row.count())
                        .rate(rate(row.count(), nonRegisteredTargetCount))
                        .build())
                .toList();

        return AnalysisReportFollowUpResponse.builder()
                .month(yearMonth.format(MONTH_FORMATTER))
                .summary(summary)
                .registrationChange(AnalysisReportFollowUpResponse.RegistrationChange.builder()
                        .beforeRate(beforeRate)
                        .currentRate(currentRate)
                        .changePoint(currentRate - beforeRate)
                        .build())
                .conversionGraph(AnalysisReportFollowUpResponse.ConversionGraph.builder()
                        .initialNonRegisteredCount(graphCounts.initialNonRegisteredCount())
                        .rounds(rounds)
                        .finalRegisteredCount(graphCounts.finalRegisteredCount())
                        .nonRegisteredOrLostCount(graphCounts.nonRegisteredOrLostCount())
                        .unattributedRegisteredCount(graphCounts.unattributedRegisteredCount())
                        .build())
                .nonConversionReasons(nonConversionReasons)
                .aiRecommendations(buildRecommendations(summary, rounds, nonConversionReasons))
                .build();
    }

    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now(SEOUL_ZONE);
        }

        try {
            return YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(AnalysisReportErrorCode.INVALID_MONTH_FORMAT);
        }
    }

    private int rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }

    private String displayReasonName(String reasonType) {
        return REASON_DISPLAY_NAMES.getOrDefault(reasonType, reasonType);
    }

    private List<AnalysisReportFollowUpResponse.AiRecommendation> buildRecommendations(
            AnalysisReportFollowUpResponse.Summary summary,
            List<AnalysisReportFollowUpResponse.ConversionRound> rounds,
            List<AnalysisReportFollowUpResponse.NonConversionReasonItem> reasons
    ) {
        List<AnalysisReportFollowUpResponse.AiRecommendation> recommendations = new ArrayList<>();

        if (!reasons.isEmpty()) {
            AnalysisReportFollowUpResponse.NonConversionReasonItem topReason = reasons.get(0);
            recommendations.add(AnalysisReportFollowUpResponse.AiRecommendation.builder()
                    .title(topReason.getDisplayName() + " 대응 메시지를 우선 점검하세요")
                    .description("미등록 사유에서 " + topReason.getDisplayName()
                            + " 비중이 높으므로 다음 연락 메시지와 상담 제안을 이 사유 중심으로 조정합니다.")
                    .build());
        }

        ConversionRoundCountsView strongestRound = findStrongestRound(rounds);
        if (strongestRound != null && strongestRound.registeredCount() > 0) {
            recommendations.add(AnalysisReportFollowUpResponse.AiRecommendation.builder()
                    .title(strongestRound.contactRound() + "차 연락 전환 패턴을 재사용하세요")
                    .description(strongestRound.contactRound()
                            + "차 연락에서 등록 전환이 가장 많이 발생했으므로 해당 차수의 메시지 톤과 제안을 우선 검토합니다.")
                    .build());
        }

        if (summary.getTargetCustomerCount() > 0
                && summary.getFollowUpRegistrationConversionRate() < 30) {
            recommendations.add(AnalysisReportFollowUpResponse.AiRecommendation.builder()
                    .title("후속 등록 전환율을 높일 실험이 필요합니다")
                    .description("전환율이 낮은 편이므로 미등록 사유별 메시지 문구와 연락 시점을 나누어 테스트합니다.")
                    .build());
        }

        if (recommendations.isEmpty()) {
            recommendations.add(AnalysisReportFollowUpResponse.AiRecommendation.builder()
                    .title("현재 후속관리 흐름을 유지하며 데이터를 누적하세요")
                    .description("선택 월의 전환과 미등록 사유 데이터가 적어 명확한 개선 신호가 아직 크지 않습니다.")
                    .build());
        }

        return recommendations;
    }

    private ConversionRoundCountsView findStrongestRound(List<AnalysisReportFollowUpResponse.ConversionRound> rounds) {
        return rounds.stream()
                .map(round -> new ConversionRoundCountsView(round.getContactRound(), round.getRegisteredCount()))
                .max((left, right) -> {
                    int countCompare = Long.compare(left.registeredCount(), right.registeredCount());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    return Integer.compare(right.contactRound(), left.contactRound());
                })
                .orElse(null);
    }

    private record ConversionRoundCountsView(int contactRound, long registeredCount) {
    }
}
