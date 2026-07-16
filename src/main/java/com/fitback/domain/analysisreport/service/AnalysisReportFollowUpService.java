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
import java.util.LinkedHashMap;
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
        List<AiInsightPatternCount> insightPatternCounts = queryRepository.findAiInsightPatternCounts(
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
                .aiRecommendations(buildRecommendations(summary, rounds, nonConversionReasons, graphCounts, insightPatternCounts))
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
            List<AnalysisReportFollowUpResponse.NonConversionReasonItem> reasons,
            ConversionGraphCounts graphCounts,
            List<AiInsightPatternCount> insightPatterns
    ) {
        List<AnalysisReportFollowUpResponse.AiRecommendation> recommendations = new ArrayList<>();

        if (isInsufficientData(summary, rounds, reasons)) {
            recommendations.add(dataAccumulationRecommendation());
            recommendations.add(reasonRecordingRecommendation());
            recommendations.add(firstContactStandardRecommendation());
            return limitRecommendations(recommendations);
        }

        addIfNotNull(recommendations, buildReasonRecommendation(reasons, insightPatterns));
        addIfNotNull(recommendations, buildRoundRecommendation(rounds));
        addIfNotNull(recommendations, buildQualityRecommendation(summary, graphCounts));

        recommendations.add(dataAccumulationRecommendation());
        recommendations.add(reasonRecordingRecommendation());
        recommendations.add(firstContactStandardRecommendation());

        return limitRecommendations(recommendations);
    }

    private boolean isInsufficientData(
            AnalysisReportFollowUpResponse.Summary summary,
            List<AnalysisReportFollowUpResponse.ConversionRound> rounds,
            List<AnalysisReportFollowUpResponse.NonConversionReasonItem> reasons
    ) {
        long registeredByRound = rounds.stream()
                .mapToLong(AnalysisReportFollowUpResponse.ConversionRound::getRegisteredCount)
                .sum();
        return summary.getTargetCustomerCount() < 5
                || (reasons.isEmpty()
                && registeredByRound == 0
                && summary.getRegisteredAfterFollowUpCount() == 0);
    }

    private AnalysisReportFollowUpResponse.AiRecommendation buildReasonRecommendation(
            List<AnalysisReportFollowUpResponse.NonConversionReasonItem> reasons,
            List<AiInsightPatternCount> insightPatterns
    ) {
        if (reasons.isEmpty()) {
            return reasonRecordingRecommendation();
        }

        AnalysisReportFollowUpResponse.NonConversionReasonItem topReason = reasons.get(0);
        boolean hasSupportingInsight = hasSupportingInsight(topReason.getReasonType(), insightPatterns);
        String insightSentence = hasSupportingInsight
                ? " AI 인사이트에서도 같은 유형의 후속관리 포인트가 반복되어 우선 점검 가치가 있습니다."
                : "";

        return switch (topReason.getReasonType()) {
            case "PRICE_BURDEN" -> recommendation(
                    "가격 부담을 낮추는 안내를 우선 강화하세요",
                    "미등록 사유에서 가격 부담 비중이 높으므로 비용 구조, 분납 가능 여부, 시작 부담이 낮은 옵션을 먼저 안내합니다."
                            + insightSentence
            );
            case "SCHEDULE_CONFLICT" -> recommendation(
                    "일정 선택지를 넓히는 후속 안내를 강화하세요",
                    "일정 불일치가 주요 미등록 사유이므로 가능한 방문 시간대와 대체 일정을 먼저 제시해 예약 부담을 낮춥니다."
                            + insightSentence
            );
            case "NO_RESPONSE" -> recommendation(
                    "연락 시간대와 채널을 나누어 테스트하세요",
                    "응답 없음 비중이 높으므로 고객 선호 채널과 발송 시간대를 나누어 후속 연락 반응을 비교합니다."
                            + insightSentence
            );
            case "NEED_CONSIDERATION" -> recommendation(
                    "고민 고객에게 다음 행동을 명확히 제시하세요",
                    "고민 필요 사유가 많으므로 비교 기준, 예상 효과, 다음 방문 시 확인할 내용을 짧게 정리해 안내합니다."
                            + insightSentence
            );
            case "LOW_INTEREST" -> recommendation(
                    "관심을 다시 끌 수 있는 체험형 제안을 점검하세요",
                    "관심 낮음 사유가 많으므로 고객이 부담 없이 다시 반응할 수 있는 체험, 상담, 혜택 제안을 우선 검토합니다."
                            + insightSentence
            );
            case "COMPARING_OPTIONS" -> recommendation(
                    "비교 중인 고객에게 차별점을 먼저 정리하세요",
                    "타 옵션 비교 고객이 많으므로 서비스 강점, 상담 후 기대 변화, 선택 기준을 후속 메시지에 명확히 담습니다."
                            + insightSentence
            );
            case "START_TIMING_DELAY" -> recommendation(
                    "시작 보류 고객의 재접촉 기준일을 잡으세요",
                    "시작 시점 보류 사유가 많으므로 고객이 다시 검토하기 좋은 날짜를 정하고 해당 시점에 맞춰 재연락합니다."
                            + insightSentence
            );
            default -> recommendation(
                    topReason.getDisplayName() + " 사유를 더 구체적으로 분류하세요",
                    "상위 미등록 사유가 넓게 묶여 있으므로 상담 직후 사유를 더 구체적으로 기록해 다음 후속 연락 방향을 정합니다."
                            + insightSentence
            );
        };
    }

    private AnalysisReportFollowUpResponse.AiRecommendation buildRoundRecommendation(
            List<AnalysisReportFollowUpResponse.ConversionRound> rounds
    ) {
        long totalRegistered = rounds.stream()
                .mapToLong(AnalysisReportFollowUpResponse.ConversionRound::getRegisteredCount)
                .sum();
        long totalSent = rounds.stream()
                .mapToLong(AnalysisReportFollowUpResponse.ConversionRound::getSentCount)
                .sum();

        if (totalRegistered == 0) {
            if (totalSent >= 5) {
                return recommendation(
                        "발송량보다 메시지 내용과 제안을 점검하세요",
                        "후속 메시지는 꾸준히 발송되고 있지만 등록 전환이 적으므로 혜택, 방문 유도 문구, 상담 요약 표현을 다시 점검합니다."
                );
            }
            return recommendation(
                    "차수별 전환 귀속을 꾸준히 누적하세요",
                    "아직 차수별 등록 전환이 충분히 쌓이지 않았으므로 연락 차수와 등록 전환 기록을 먼저 안정적으로 남깁니다."
            );
        }

        ConversionRoundCountsView strongestRound = findStrongestRound(rounds);
        if (strongestRound == null) {
            return firstContactStandardRecommendation();
        }

        if (strongestRound.contactRound() == 1) {
            return recommendation(
                    "1차 후속 연락 메시지를 표준화하세요",
                    "등록 전환이 1차 연락에서 가장 많이 발생하므로 상담 직후 발송되는 메시지의 혜택, 방문 유도 문구, 상담 요약 표현을 일관되게 관리합니다."
            );
        }

        return recommendation(
                strongestRound.contactRound() + "차 재접촉 메시지를 강화하세요",
                strongestRound.contactRound()
                        + "차 연락에서 등록 전환이 가장 많이 발생하므로 재접촉 시점의 제안, 혜택, 상담 요약 문구를 우선 개선합니다."
        );
    }

    private AnalysisReportFollowUpResponse.AiRecommendation buildQualityRecommendation(
            AnalysisReportFollowUpResponse.Summary summary,
            ConversionGraphCounts graphCounts
    ) {
        if (summary.getTargetCustomerCount() > 0
                && summary.getFollowUpRegistrationConversionRate() < 30) {
            return recommendation(
                    "후속 등록 전환율을 높일 실험이 필요합니다",
                    "전환율이 낮은 편이므로 미등록 사유별 메시지 문구와 연락 시점을 나누어 테스트합니다."
            );
        }

        if (graphCounts.unattributedRegisteredCount() > 0) {
            return recommendation(
                    "등록 전환 귀속 기록을 더 정확히 남기세요",
                    "등록된 고객 중 연락 차수가 확인되지 않는 건이 있으므로 등록 전환 시점과 연결된 후속 연락 기록을 함께 남깁니다."
            );
        }

        if (summary.getPendingFollowUpCount() > summary.getCompletedFollowUpCount()
                && summary.getTargetCustomerCount() >= 5) {
            return recommendation(
                    "진행 중 후속 연락의 완료 흐름을 점검하세요",
                    "진행 중인 후속 연락이 완료 건보다 많으므로 연락 예정일, 담당자 처리 상태, 다음 액션 실행 여부를 함께 확인합니다."
            );
        }

        return recommendation(
                "현재 후속관리 흐름을 유지하며 성과를 비교하세요",
                "선택 월의 전환 흐름이 안정적으로 기록되고 있으므로 같은 기준으로 다음 달 전환율과 미등록 사유 변화를 비교합니다."
        );
    }

    private boolean hasSupportingInsight(String reasonType, List<AiInsightPatternCount> insightPatterns) {
        if (insightPatterns == null || insightPatterns.isEmpty()) {
            return false;
        }

        List<String> keywords = switch (reasonType) {
            case "PRICE_BURDEN" -> List.of("가격", "비용", "price", "cost");
            case "SCHEDULE_CONFLICT" -> List.of("일정", "시간", "schedule", "time");
            case "NO_RESPONSE" -> List.of("응답", "연락", "response", "contact");
            case "NEED_CONSIDERATION" -> List.of("고민", "검토", "consider");
            case "LOW_INTEREST" -> List.of("관심", "흥미", "interest");
            case "COMPARING_OPTIONS" -> List.of("비교", "옵션", "compare", "option");
            case "START_TIMING_DELAY" -> List.of("시작", "보류", "timing", "delay");
            default -> List.of();
        };

        return insightPatterns.stream()
                .map(AiInsightPatternCount::patternValue)
                .filter(value -> value != null)
                .map(String::toLowerCase)
                .anyMatch(value -> keywords.stream()
                        .map(String::toLowerCase)
                        .anyMatch(value::contains));
    }

    private List<AnalysisReportFollowUpResponse.AiRecommendation> limitRecommendations(
            List<AnalysisReportFollowUpResponse.AiRecommendation> recommendations
    ) {
        Map<String, AnalysisReportFollowUpResponse.AiRecommendation> unique = new LinkedHashMap<>();
        for (AnalysisReportFollowUpResponse.AiRecommendation recommendation : recommendations) {
            if (recommendation != null) {
                unique.putIfAbsent(recommendation.getTitle(), recommendation);
            }
        }

        List<AnalysisReportFollowUpResponse.AiRecommendation> result = new ArrayList<>(unique.values());
        return result.stream()
                .limit(3)
                .toList();
    }

    private void addIfNotNull(
            List<AnalysisReportFollowUpResponse.AiRecommendation> recommendations,
            AnalysisReportFollowUpResponse.AiRecommendation recommendation
    ) {
        if (recommendation != null) {
            recommendations.add(recommendation);
        }
    }

    private AnalysisReportFollowUpResponse.AiRecommendation dataAccumulationRecommendation() {
        return recommendation(
                "후속관리 데이터를 먼저 누적하세요",
                "선택 월의 후속관리 대상자나 전환 기록이 적어 명확한 패턴을 판단하기 어렵습니다. 연락 결과와 등록 전환 기록을 꾸준히 남기는 것이 우선입니다."
        );
    }

    private AnalysisReportFollowUpResponse.AiRecommendation reasonRecordingRecommendation() {
        return recommendation(
                "미등록 사유를 상담 직후에 정리하세요",
                "사유 데이터가 충분해야 가격, 일정, 고민 필요, 응답 없음 중 어떤 흐름을 개선할지 판단할 수 있습니다."
        );
    }

    private AnalysisReportFollowUpResponse.AiRecommendation firstContactStandardRecommendation() {
        return recommendation(
                "첫 후속 연락 시점을 일정하게 유지하세요",
                "충분한 데이터가 쌓이기 전까지는 상담 이후 첫 연락 시점을 일정하게 관리해 비교 가능한 운영 기준을 만듭니다."
        );
    }

    private AnalysisReportFollowUpResponse.AiRecommendation recommendation(String title, String description) {
        return AnalysisReportFollowUpResponse.AiRecommendation.builder()
                .title(title)
                .description(description)
                .build();
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
