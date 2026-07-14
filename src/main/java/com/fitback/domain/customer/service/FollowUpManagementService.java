package com.fitback.domain.customer.service;

import com.fitback.domain.customer.dto.response.FollowUpSummaryResponse;
import com.fitback.domain.customer.exception.FollowUpManagementErrorCode;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.SummaryCounts;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowUpManagementService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT);

    private final FollowUpManagementQueryRepository queryRepository;

    public FollowUpSummaryResponse getSummary(UUID storeId, String month) {
        validateStoreId(storeId);

        LocalDate today = LocalDate.now(Clock.system(CustomerManagementQueryValidator.SERVICE_ZONE_ID));
        YearMonth targetMonth = resolveMonth(month);
        SummaryCounts counts = queryRepository.findSummaryCounts(
                storeId,
                today,
                targetMonth.atDay(1),
                targetMonth.plusMonths(1).atDay(1)
        );

        return FollowUpSummaryResponse.builder()
                .todayPendingCount(counts.todayPendingCount())
                .secondRoundPendingCount(counts.secondRoundPendingCount())
                .overdueCount(counts.overdueCount())
                .thisMonthPendingCount(counts.thisMonthPendingCount())
                .build();
    }

    private void validateStoreId(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(FollowUpManagementErrorCode.STORE_NOT_ASSIGNED);
        }
    }

    private YearMonth resolveMonth(String month) {
        try {
            if (month == null || month.isBlank()) {
                return YearMonth.now(Clock.system(CustomerManagementQueryValidator.SERVICE_ZONE_ID));
            }
            return YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
