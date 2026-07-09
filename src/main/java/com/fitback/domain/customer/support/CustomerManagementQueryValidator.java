package com.fitback.domain.customer.support;

import com.fitback.domain.customer.exception.CustomerManagementErrorCode;
import com.fitback.global.exception.BusinessException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class CustomerManagementQueryValidator {

    public static final int MAX_KEYWORD_LENGTH = 100;
    public static final int MAX_PAGE_SIZE = 100;
    public static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT);

    private CustomerManagementQueryValidator() {
    }

    public static MonthRange resolveMonthRange(String month) {
        return resolveMonthRange(month, Clock.system(SERVICE_ZONE_ID));
    }

    static MonthRange resolveMonthRange(String month, Clock clock) {
        YearMonth yearMonth;
        try {
            yearMonth = month == null || month.isBlank()
                    ? YearMonth.now(clock.withZone(SERVICE_ZONE_ID))
                    : YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_MONTH_FORMAT);
        }

        OffsetDateTime startInclusive = yearMonth.atDay(1)
                .atStartOfDay(SERVICE_ZONE_ID)
                .toOffsetDateTime();
        OffsetDateTime endExclusive = yearMonth.plusMonths(1)
                .atDay(1)
                .atStartOfDay(SERVICE_ZONE_ID)
                .toOffsetDateTime();

        return new MonthRange(yearMonth, startInclusive, endExclusive);
    }

    public static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_SEARCH_CONDITION);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    public static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_PAGE_REQUEST);
        }
    }

    public record MonthRange(
            YearMonth month,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    ) {
    }
}
