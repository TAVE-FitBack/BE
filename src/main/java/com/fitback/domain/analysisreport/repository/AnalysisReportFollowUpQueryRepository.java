package com.fitback.domain.analysisreport.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AnalysisReportFollowUpQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SummaryCounts findSummaryCounts(
            UUID storeId,
            OffsetDateTime monthStartInclusive,
            OffsetDateTime monthEndExclusive
    ) {
        String sql = targetCustomersCte() + """
                , latest_follow_up AS (
                    SELECT DISTINCT ON (f.customer_id)
                           f.customer_id,
                           f.status
                    FROM follow_up f
                    JOIN target_customers tc ON tc.customer_id = f.customer_id
                    WHERE f.status <> 'SUPERSEDED'
                    ORDER BY f.customer_id, f.created_at DESC, f.id DESC
                )
                SELECT
                    COUNT(*) AS target_customer_count,
                    COUNT(*) FILTER (
                        WHERE EXISTS (
                            SELECT 1
                            FROM follow_up f
                            WHERE f.customer_id = tc.customer_id
                              AND f.status = 'PENDING'
                        )
                    ) AS pending_follow_up_count,
                    COUNT(*) FILTER (
                        WHERE lfu.status IN ('COMPLETED', 'CLOSED')
                    ) AS completed_follow_up_count,
                    COUNT(*) FILTER (
                        WHERE tc.status = 'REGISTERED'
                          AND tc.registered_at IS NOT NULL
                          AND tc.registered_at >= tc.first_follow_up_created_at
                    ) AS registered_after_follow_up_count
                FROM target_customers tc
                LEFT JOIN latest_follow_up lfu ON lfu.customer_id = tc.customer_id
                """;

        MapSqlParameterSource params = monthParams(storeId, monthStartInclusive, monthEndExclusive);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SummaryCounts(
                rs.getLong("target_customer_count"),
                rs.getLong("pending_follow_up_count"),
                rs.getLong("completed_follow_up_count"),
                rs.getLong("registered_after_follow_up_count")
        ));
    }

    public RegistrationChangeCounts findRegistrationChangeCounts(
            UUID storeId,
            LocalDate monthStartInclusive,
            LocalDate monthEndExclusive
    ) {
        String sql = """
                WITH first_follow_up AS (
                    SELECT f.customer_id,
                           MIN(f.created_at) AS first_follow_up_created_at
                    FROM follow_up f
                    WHERE f.status <> 'SUPERSEDED'
                    GROUP BY f.customer_id
                ),
                monthly_customers AS (
                    SELECT c.id AS customer_id,
                           c.status,
                           c.registered_at,
                           ffu.first_follow_up_created_at
                    FROM customer c
                    LEFT JOIN first_follow_up ffu ON ffu.customer_id = c.id
                    WHERE c.store_id = :storeId
                      AND c.first_consult_at >= :monthStartDate
                      AND c.first_consult_at < :monthEndDate
                )
                SELECT
                    COUNT(*) AS monthly_customer_count,
                    COUNT(*) FILTER (
                        WHERE status = 'REGISTERED'
                          AND registered_at IS NOT NULL
                          AND (
                              first_follow_up_created_at IS NULL
                              OR registered_at < first_follow_up_created_at
                          )
                    ) AS registered_before_follow_up_count,
                    COUNT(*) FILTER (
                        WHERE status = 'REGISTERED'
                    ) AS current_registered_count
                FROM monthly_customers
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("monthStartDate", monthStartInclusive)
                .addValue("monthEndDate", monthEndExclusive);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new RegistrationChangeCounts(
                rs.getLong("monthly_customer_count"),
                rs.getLong("registered_before_follow_up_count"),
                rs.getLong("current_registered_count")
        ));
    }

    public ConversionGraphCounts findConversionGraphCounts(
            UUID storeId,
            OffsetDateTime monthStartInclusive,
            OffsetDateTime monthEndExclusive
    ) {
        String sql = targetCustomersCte() + """
                SELECT
                    COUNT(*) AS initial_non_registered_count,
                    COUNT(DISTINCT tc.customer_id) FILTER (
                        WHERE fuc.contact_round IN (1, 2, 3)
                    ) AS final_registered_count,
                    COUNT(*) FILTER (
                        WHERE tc.status <> 'REGISTERED'
                    ) AS non_registered_or_lost_count,
                    COUNT(DISTINCT tc.customer_id) FILTER (
                        WHERE tc.status = 'REGISTERED'
                          AND (
                              fuc.id IS NULL
                              OR fuc.contact_round IS NULL
                          )
                    ) AS unattributed_registered_count
                FROM target_customers tc
                LEFT JOIN follow_up_conversion fuc ON fuc.customer_id = tc.customer_id
                """;

        MapSqlParameterSource params = monthParams(storeId, monthStartInclusive, monthEndExclusive);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ConversionGraphCounts(
                rs.getLong("initial_non_registered_count"),
                rs.getLong("final_registered_count"),
                rs.getLong("non_registered_or_lost_count"),
                rs.getLong("unattributed_registered_count")
        ));
    }

    public List<ConversionRoundCounts> findConversionRoundCounts(
            UUID storeId,
            OffsetDateTime monthStartInclusive,
            OffsetDateTime monthEndExclusive
    ) {
        String sql = targetCustomersCte() + """
                , rounds AS (
                    SELECT 1 AS contact_round
                    UNION ALL SELECT 2
                    UNION ALL SELECT 3
                )
                SELECT
                    r.contact_round,
                    COUNT(DISTINCT fuc.customer_id) AS registered_count,
                    COUNT(DISTINCT mt.customer_id) AS sent_count,
                    COUNT(DISTINCT fp.customer_id) AS pending_count
                FROM rounds r
                LEFT JOIN follow_up_conversion fuc
                       ON fuc.contact_round = r.contact_round
                      AND fuc.customer_id IN (SELECT customer_id FROM target_customers)
                LEFT JOIN message_template mt
                       ON mt.contact_round = r.contact_round
                      AND mt.sent_at IS NOT NULL
                      AND mt.customer_id IN (SELECT customer_id FROM target_customers)
                LEFT JOIN follow_up fp
                       ON fp.contact_round = r.contact_round
                      AND fp.status = 'PENDING'
                      AND fp.customer_id IN (SELECT customer_id FROM target_customers)
                GROUP BY r.contact_round
                ORDER BY r.contact_round
                """;

        MapSqlParameterSource params = monthParams(storeId, monthStartInclusive, monthEndExclusive);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ConversionRoundCounts(
                rs.getInt("contact_round"),
                rs.getLong("registered_count"),
                rs.getLong("sent_count"),
                rs.getLong("pending_count")
        ));
    }

    public long findNonRegisteredTargetCount(
            UUID storeId,
            OffsetDateTime monthStartInclusive,
            OffsetDateTime monthEndExclusive
    ) {
        String sql = targetCustomersCte() + """
                SELECT COUNT(*) AS non_registered_count
                FROM target_customers
                WHERE status <> 'REGISTERED'
                """;

        Long result = jdbcTemplate.queryForObject(
                sql,
                monthParams(storeId, monthStartInclusive, monthEndExclusive),
                Long.class
        );
        return result != null ? result : 0;
    }

    public List<NonConversionReasonCount> findNonConversionReasonCounts(
            UUID storeId,
            OffsetDateTime monthStartInclusive,
            OffsetDateTime monthEndExclusive
    ) {
        String sql = targetCustomersCte() + """
                SELECT
                    ncr.reason_type,
                    COUNT(DISTINCT ncr.customer_id) AS reason_count
                FROM target_customers tc
                JOIN non_conversion_reason ncr ON ncr.customer_id = tc.customer_id
                WHERE tc.status <> 'REGISTERED'
                GROUP BY ncr.reason_type
                ORDER BY reason_count DESC, ncr.reason_type ASC
                """;

        return jdbcTemplate.query(
                sql,
                monthParams(storeId, monthStartInclusive, monthEndExclusive),
                (rs, rowNum) -> new NonConversionReasonCount(
                        rs.getString("reason_type"),
                        rs.getLong("reason_count")
                )
        );
    }

    private String targetCustomersCte() {
        return """
                WITH first_follow_up AS (
                    SELECT f.customer_id,
                           MIN(f.created_at) AS first_follow_up_created_at
                    FROM follow_up f
                    JOIN customer c ON c.id = f.customer_id
                    WHERE c.store_id = :storeId
                      AND f.status <> 'SUPERSEDED'
                    GROUP BY f.customer_id
                ),
                target_customers AS (
                    SELECT c.id AS customer_id,
                           c.status,
                           c.registered_at,
                           ffu.first_follow_up_created_at
                    FROM first_follow_up ffu
                    JOIN customer c ON c.id = ffu.customer_id
                    WHERE ffu.first_follow_up_created_at >= :monthStart
                      AND ffu.first_follow_up_created_at < :monthEnd
                )
                """;
    }

    private MapSqlParameterSource monthParams(
            UUID storeId,
            OffsetDateTime monthStartInclusive,
            OffsetDateTime monthEndExclusive
    ) {
        return new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("monthStart", monthStartInclusive)
                .addValue("monthEnd", monthEndExclusive);
    }

    public record SummaryCounts(
            long targetCustomerCount,
            long pendingFollowUpCount,
            long completedFollowUpCount,
            long registeredAfterFollowUpCount
    ) {
    }

    public record RegistrationChangeCounts(
            long monthlyCustomerCount,
            long registeredBeforeFollowUpCount,
            long currentRegisteredCount
    ) {
    }

    public record ConversionGraphCounts(
            long initialNonRegisteredCount,
            long finalRegisteredCount,
            long nonRegisteredOrLostCount,
            long unattributedRegisteredCount
    ) {
    }

    public record ConversionRoundCounts(
            int contactRound,
            long registeredCount,
            long sentCount,
            long pendingCount
    ) {
    }

    public record NonConversionReasonCount(
            String reasonType,
            long count
    ) {
    }
}
