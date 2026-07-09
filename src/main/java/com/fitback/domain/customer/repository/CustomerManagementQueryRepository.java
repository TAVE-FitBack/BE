package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.support.CustomerManagementQueryValidator.MonthRange;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CustomerManagementQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SummaryCounts findSummaryCounts(UUID storeId, MonthRange range) {
        String sql = """
                WITH monthly_consulted_customers AS (
                    SELECT DISTINCT c.id, c.status
                    FROM customer c
                    JOIN consultation co ON co.customer_id = c.id
                    WHERE c.store_id = :storeId
                      AND co.consulted_at >= :startAt
                      AND co.consulted_at < :endAt
                )
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM customer c
                        WHERE c.store_id = :storeId
                          AND c.first_consult_at >= :startDate
                          AND c.first_consult_at < :endDate
                    ) AS new_consultation_count,
                    (
                        SELECT COUNT(*)
                        FROM customer c
                        WHERE c.store_id = :storeId
                          AND c.registered_at >= :startAt
                          AND c.registered_at < :endAt
                    ) AS new_registration_count,
                    (
                        SELECT COUNT(*)
                        FROM customer c
                        WHERE c.store_id = :storeId
                          AND c.first_consult_at >= :startDate
                          AND c.first_consult_at < :endDate
                          AND c.status <> 'REGISTERED'
                    ) AS non_registered_count,
                    (SELECT COUNT(*) FROM monthly_consulted_customers) AS consulted_customer_count,
                    (
                        SELECT COUNT(*)
                        FROM monthly_consulted_customers
                        WHERE status = 'REGISTERED'
                    ) AS registered_consulted_customer_count
                """;

        return jdbcTemplate.queryForObject(
                sql,
                parameters(storeId, range),
                (rs, rowNum) -> new SummaryCounts(
                        rs.getLong("new_consultation_count"),
                        rs.getLong("new_registration_count"),
                        rs.getLong("non_registered_count"),
                        rs.getLong("consulted_customer_count"),
                        rs.getLong("registered_consulted_customer_count")
                )
        );
    }

    public List<ServiceCountRow> findServiceCounts(UUID storeId, MonthRange range) {
        String sql = """
                WITH monthly_consulted_customers AS (
                    SELECT DISTINCT c.id AS customer_id
                    FROM customer c
                    JOIN consultation co ON co.customer_id = c.id
                    WHERE c.store_id = :storeId
                      AND co.consulted_at >= :startAt
                      AND co.consulted_at < :endAt
                ),
                service_customers AS (
                    SELECT DISTINCT c.id AS customer_id, co.consulted_service_id AS service_id
                    FROM customer c
                    JOIN consultation co ON co.customer_id = c.id
                    WHERE c.store_id = :storeId
                      AND co.consulted_at >= :startAt
                      AND co.consulted_at < :endAt
                    UNION
                    SELECT mc.customer_id, i.service_id
                    FROM monthly_consulted_customers mc
                    JOIN interest_service i ON i.customer_id = mc.customer_id
                )
                SELECT s.id AS service_id, s.name AS service_name, COUNT(*) AS customer_count
                FROM service_customers sc
                JOIN service s ON s.id = sc.service_id
                WHERE s.store_id = :storeId
                GROUP BY s.id, s.name
                ORDER BY customer_count DESC, s.name ASC
                """;

        return jdbcTemplate.query(
                sql,
                parameters(storeId, range),
                (rs, rowNum) -> new ServiceCountRow(
                        rs.getObject("service_id", UUID.class),
                        rs.getString("service_name"),
                        rs.getLong("customer_count")
                )
        );
    }

    public List<InflowPathCountRow> findInflowPathCounts(UUID storeId, MonthRange range) {
        String sql = """
                WITH monthly_consulted_customers AS (
                    SELECT DISTINCT c.id AS customer_id, c.inflow_path_id
                    FROM customer c
                    JOIN consultation co ON co.customer_id = c.id
                    WHERE c.store_id = :storeId
                      AND co.consulted_at >= :startAt
                      AND co.consulted_at < :endAt
                )
                SELECT ip.id AS inflow_path_id, ip.name AS inflow_path_name, COUNT(*) AS customer_count
                FROM monthly_consulted_customers mc
                JOIN inflow_path_option ip ON ip.id = mc.inflow_path_id
                WHERE ip.store_id = :storeId
                GROUP BY ip.id, ip.name
                ORDER BY customer_count DESC, ip.name ASC
                """;

        return jdbcTemplate.query(
                sql,
                parameters(storeId, range),
                (rs, rowNum) -> new InflowPathCountRow(
                        rs.getObject("inflow_path_id", UUID.class),
                        rs.getString("inflow_path_name"),
                        rs.getLong("customer_count")
                )
        );
    }

    private MapSqlParameterSource parameters(UUID storeId, MonthRange range) {
        LocalDate startDate = range.startInclusive().toLocalDate();
        LocalDate endDate = range.endExclusive().toLocalDate();
        return new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("startAt", range.startInclusive())
                .addValue("endAt", range.endExclusive())
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);
    }

    public record SummaryCounts(
            long newConsultationCount,
            long newRegistrationCount,
            long nonRegisteredCount,
            long consultedCustomerCount,
            long registeredConsultedCustomerCount
    ) {
    }

    public record ServiceCountRow(
            UUID serviceId,
            String serviceName,
            long customerCount
    ) {
    }

    public record InflowPathCountRow(
            UUID inflowPathId,
            String inflowPathName,
            long customerCount
    ) {
    }
}
