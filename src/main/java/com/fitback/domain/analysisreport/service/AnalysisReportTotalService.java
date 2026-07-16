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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisReportTotalService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final String DIRECTION_UP = "UP";
    private static final String DIRECTION_DOWN = "DOWN";
    private static final String DIRECTION_FLAT = "FLAT";
    private static final String ALL_SERIES_KEY = "ALL";
    private static final String ALL_SERIES_NAME = "전체";

    private final AnalysisReportTotalQueryRepository queryRepository;

    public AnalysisReportTotalResponse getTotalReport(UUID storeId, String month) {
        if (storeId == null) {
            throw new BusinessException(AnalysisReportErrorCode.STORE_NOT_ASSIGNED);
        }

        YearMonth selectedMonth = parseMonth(month);
        LocalDate monthStart = selectedMonth.atDay(1);
        LocalDate monthEnd = selectedMonth.plusMonths(1).atDay(1);
        LocalDate previousMonthStart = selectedMonth.minusMonths(1).atDay(1);
        LocalDate trendStart = selectedMonth.minusMonths(5).atDay(1);
        LocalDate trendEnd = monthEnd;

        SummaryCounts summaryCounts = queryRepository.findSummaryCounts(storeId, monthStart, monthEnd);
        List<ServiceConsultationCounts> serviceCounts = queryRepository.findServiceConsultationCounts(
                storeId,
                monthStart,
                monthEnd
        );
        List<MonthlyRegistrationCounts> monthlyRegistrationCounts = queryRepository.findMonthlyRegistrationCounts(
                storeId,
                trendStart,
                trendEnd
        );
        List<MonthlyServiceRegistrationCounts> monthlyServiceRegistrationCounts =
                queryRepository.findMonthlyServiceRegistrationCounts(storeId, trendStart, trendEnd);
        RegistrationCounts previousMonthCounts = queryRepository.findRegistrationCounts(
                storeId,
                previousMonthStart,
                monthStart
        );
        List<InflowPathCounts> inflowPathCounts = queryRepository.findInflowPathCounts(storeId, monthStart, monthEnd);

        int selectedMonthRate = rate(
                summaryCounts.newRegistrationCount(),
                summaryCounts.newConsultationCount()
        );
        int previousMonthRate = rate(
                previousMonthCounts.registeredCount(),
                previousMonthCounts.consultedCount()
        );
        int monthOverMonthChangePoint = selectedMonthRate - previousMonthRate;

        return AnalysisReportTotalResponse.builder()
                .month(selectedMonth.format(MONTH_FORMATTER))
                .summary(buildSummary(summaryCounts, selectedMonthRate))
                .serviceConsultations(buildServiceConsultations(serviceCounts, summaryCounts.newConsultationCount()))
                .registrationTrend(AnalysisReportTotalResponse.RegistrationTrend.builder()
                        .months(monthlyRegistrationCounts.stream()
                                .map(MonthlyRegistrationCounts::month)
                                .toList())
                        .selectedMonthRate(selectedMonthRate)
                        .previousMonthRate(previousMonthRate)
                        .monthOverMonthChangePoint(monthOverMonthChangePoint)
                        .direction(direction(monthOverMonthChangePoint))
                        .series(buildRegistrationTrendSeries(
                                monthlyRegistrationCounts,
                                monthlyServiceRegistrationCounts
                        ))
                        .build())
                .inflowPaths(buildInflowPaths(
                        inflowPathCounts,
                        summaryCounts.newConsultationCount(),
                        summaryCounts.newRegistrationCount()
                ))
                .build();
    }

    private AnalysisReportTotalResponse.Summary buildSummary(
            SummaryCounts summaryCounts,
            int newRegistrationRate
    ) {
        return AnalysisReportTotalResponse.Summary.builder()
                .newRegistrationRate(newRegistrationRate)
                .newConsultationCount(summaryCounts.newConsultationCount())
                .newRegistrationCount(summaryCounts.newRegistrationCount())
                .nonRegisteredCount(summaryCounts.nonRegisteredCount())
                .build();
    }

    private List<AnalysisReportTotalResponse.ServiceConsultation> buildServiceConsultations(
            List<ServiceConsultationCounts> serviceCounts,
            long totalNewConsultationCount
    ) {
        return serviceCounts.stream()
                .map(row -> AnalysisReportTotalResponse.ServiceConsultation.builder()
                        .serviceId(row.serviceId())
                        .serviceName(row.serviceName())
                        .consultedCustomerCount(row.consultedCustomerCount())
                        .totalNewConsultationCount(totalNewConsultationCount)
                        .consultationRate(rate(row.consultedCustomerCount(), totalNewConsultationCount))
                        .build())
                .toList();
    }

    private List<AnalysisReportTotalResponse.Series> buildRegistrationTrendSeries(
            List<MonthlyRegistrationCounts> monthlyRegistrationCounts,
            List<MonthlyServiceRegistrationCounts> monthlyServiceRegistrationCounts
    ) {
        List<AnalysisReportTotalResponse.Series> series = new ArrayList<>();
        series.add(AnalysisReportTotalResponse.Series.builder()
                .key(ALL_SERIES_KEY)
                .name(ALL_SERIES_NAME)
                .points(monthlyRegistrationCounts.stream()
                        .map(row -> AnalysisReportTotalResponse.Point.builder()
                                .month(row.month())
                                .rate(rate(row.registeredCount(), row.consultedCount()))
                                .consultedCount(row.consultedCount())
                                .registeredCount(row.registeredCount())
                                .build())
                        .toList())
                .build());

        Map<UUID, ServiceSeriesBuilder> serviceSeriesBuilders = new LinkedHashMap<>();
        for (MonthlyServiceRegistrationCounts row : monthlyServiceRegistrationCounts) {
            ServiceSeriesBuilder builder = serviceSeriesBuilders.computeIfAbsent(
                    row.serviceId(),
                    serviceId -> new ServiceSeriesBuilder(serviceId, row.serviceName())
            );
            builder.addPoint(AnalysisReportTotalResponse.Point.builder()
                    .month(row.month())
                    .rate(rate(row.registeredCount(), row.consultedCount()))
                    .consultedCount(row.consultedCount())
                    .registeredCount(row.registeredCount())
                    .build());
        }

        serviceSeriesBuilders.values().stream()
                .map(ServiceSeriesBuilder::build)
                .forEach(series::add);

        return series;
    }

    private List<AnalysisReportTotalResponse.InflowPath> buildInflowPaths(
            List<InflowPathCounts> inflowPathCounts,
            long totalNewConsultationCount,
            long totalNewRegistrationCount
    ) {
        return inflowPathCounts.stream()
                .map(row -> AnalysisReportTotalResponse.InflowPath.builder()
                        .inflowPathId(row.inflowPathId())
                        .inflowPathName(row.inflowPathName())
                        .newConsultationCompositionRate(rate(
                                row.newConsultationCount(),
                                totalNewConsultationCount
                        ))
                        .registeredCompositionRate(rate(row.registeredCount(), totalNewRegistrationCount))
                        .newConsultationCount(row.newConsultationCount())
                        .registeredCount(row.registeredCount())
                        .build())
                .toList();
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

    private String direction(int changePoint) {
        if (changePoint > 0) {
            return DIRECTION_UP;
        }
        if (changePoint < 0) {
            return DIRECTION_DOWN;
        }
        return DIRECTION_FLAT;
    }

    private static class ServiceSeriesBuilder {
        private final UUID serviceId;
        private final String serviceName;
        private final List<AnalysisReportTotalResponse.Point> points = new ArrayList<>();

        private ServiceSeriesBuilder(UUID serviceId, String serviceName) {
            this.serviceId = serviceId;
            this.serviceName = serviceName;
        }

        private void addPoint(AnalysisReportTotalResponse.Point point) {
            points.add(point);
        }

        private AnalysisReportTotalResponse.Series build() {
            return AnalysisReportTotalResponse.Series.builder()
                    .key(serviceId.toString())
                    .name(serviceName)
                    .points(points)
                    .build();
        }
    }
}
