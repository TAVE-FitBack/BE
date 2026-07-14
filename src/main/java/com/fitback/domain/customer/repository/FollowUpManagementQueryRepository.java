package com.fitback.domain.customer.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FollowUpManagementQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SummaryCounts findSummaryCounts(
            UUID storeId,
            LocalDate today,
            LocalDate monthStartInclusive,
            LocalDate monthEndExclusive
    ) {
        String sql = """
                SELECT
                    COUNT(*) FILTER (
                        WHERE f.status = 'PENDING'
                          AND f.recommend_contact_date = :today
                    ) AS today_pending_count,
                    COUNT(*) FILTER (
                        WHERE f.status = 'PENDING'
                          AND f.contact_round = 2
                    ) AS second_round_pending_count,
                    COUNT(*) FILTER (
                        WHERE f.status = 'PENDING'
                          AND f.recommend_contact_date < :today
                    ) AS overdue_count,
                    COUNT(*) FILTER (
                        WHERE f.status = 'PENDING'
                          AND f.recommend_contact_date >= :monthStartInclusive
                          AND f.recommend_contact_date < :monthEndExclusive
                    ) AS this_month_pending_count
                FROM follow_up f
                JOIN customer c ON c.id = f.customer_id
                WHERE c.store_id = :storeId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("today", today)
                .addValue("monthStartInclusive", monthStartInclusive)
                .addValue("monthEndExclusive", monthEndExclusive);

        return jdbcTemplate.queryForObject(
                sql,
                params,
                (rs, rowNum) -> new SummaryCounts(
                        rs.getLong("today_pending_count"),
                        rs.getLong("second_round_pending_count"),
                        rs.getLong("overdue_count"),
                        rs.getLong("this_month_pending_count")
                )
        );
    }

    public record SummaryCounts(
            long todayPendingCount,
            long secondRoundPendingCount,
            long overdueCount,
            long thisMonthPendingCount
    ) {
    }
}
