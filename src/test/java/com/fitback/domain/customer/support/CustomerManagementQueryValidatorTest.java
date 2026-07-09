package com.fitback.domain.customer.support;

import com.fitback.domain.customer.exception.CustomerManagementErrorCode;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerManagementQueryValidatorTest {

    @Test
    @DisplayName("월 문자열을 서울 시간 기준의 시작 포함, 다음 달 시작 미포함 범위로 변환한다")
    void resolveMonthRange() {
        CustomerManagementQueryValidator.MonthRange range =
                CustomerManagementQueryValidator.resolveMonthRange("2026-10");

        assertThat(range.month().toString()).isEqualTo("2026-10");
        assertThat(range.startInclusive().toString()).isEqualTo("2026-10-01T00:00+09:00");
        assertThat(range.endExclusive().toString()).isEqualTo("2026-11-01T00:00+09:00");
    }

    @Test
    @DisplayName("월이 없으면 서울 시간 기준 현재 월을 사용한다")
    void resolveCurrentMonthWhenMonthIsMissing() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:30:00Z"), ZoneOffset.UTC);

        CustomerManagementQueryValidator.MonthRange range =
                CustomerManagementQueryValidator.resolveMonthRange(null, clock);

        assertThat(range.month().toString()).isEqualTo("2026-10");
    }

    @Test
    @DisplayName("잘못된 월 형식이면 INVALID_MONTH_FORMAT 예외가 발생한다")
    void rejectInvalidMonth() {
        assertErrorCode(
                () -> CustomerManagementQueryValidator.resolveMonthRange("2026-13"),
                CustomerManagementErrorCode.INVALID_MONTH_FORMAT
        );
    }

    @Test
    @DisplayName("검색어 앞뒤 공백을 제거하고 빈 검색어는 null로 정규화한다")
    void normalizeKeyword() {
        assertThat(CustomerManagementQueryValidator.normalizeKeyword("  김민지  ")).isEqualTo("김민지");
        assertThat(CustomerManagementQueryValidator.normalizeKeyword("   ")).isNull();
        assertThat(CustomerManagementQueryValidator.normalizeKeyword(null)).isNull();
    }

    @Test
    @DisplayName("검색어가 100자를 초과하면 INVALID_SEARCH_CONDITION 예외가 발생한다")
    void rejectLongKeyword() {
        assertErrorCode(
                () -> CustomerManagementQueryValidator.normalizeKeyword("가".repeat(101)),
                CustomerManagementErrorCode.INVALID_SEARCH_CONDITION
        );
    }

    @Test
    @DisplayName("페이지 번호와 크기가 허용 범위를 벗어나면 INVALID_PAGE_REQUEST 예외가 발생한다")
    void rejectInvalidPage() {
        assertErrorCode(
                () -> CustomerManagementQueryValidator.validatePage(-1, 10),
                CustomerManagementErrorCode.INVALID_PAGE_REQUEST
        );
        assertErrorCode(
                () -> CustomerManagementQueryValidator.validatePage(0, 0),
                CustomerManagementErrorCode.INVALID_PAGE_REQUEST
        );
        assertErrorCode(
                () -> CustomerManagementQueryValidator.validatePage(0, 101),
                CustomerManagementErrorCode.INVALID_PAGE_REQUEST
        );
    }

    @Test
    @DisplayName("페이지 번호 0과 페이지 크기 1부터 100까지 허용한다")
    void acceptValidPage() {
        CustomerManagementQueryValidator.validatePage(0, 1);
        CustomerManagementQueryValidator.validatePage(10, 100);
    }

    private void assertErrorCode(Runnable action, CustomerManagementErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }
}
