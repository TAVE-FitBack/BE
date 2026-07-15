package com.fitback.domain.analysisreport.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AnalysisReportTotalQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SummaryCounts findSummaryCounts(
            UUID storeId,
            LocalDate monthStartInclusive,
            LocalDate monthEndExclusive
    ) {
        String sql = monthlyCustomersCte() + """
                SELECT
                    COUNT(*) AS new_consultation_count,
                    COUNT(*) FILTER (WHERE status = 'REGISTERED') AS new_registration_count,
                    COUNT(*) FILTER (WHERE status <> 'REGISTERED') AS non_registered_count
                FROM monthly_customers
                """;

        return jdbcTemplate.queryForObject(
                sql,
                monthParams(storeId, monthStartInclusive, monthEndExclusive),
                (rs, rowNum) -> new SummaryCounts(
                        rs.getLong("new_consultation_count"),
                        rs.getLong("new_registration_count"),
                        rs.getLong("non_registered_count")
                )
        );
    }

    public List<ServiceConsultationCounts> findServiceConsultationCounts(
            UUID storeId,
            LocalDate monthStartInclusive,
            LocalDate monthEndExclusive
    ) {
        String sql = monthlyCustomersCte() + """
                SELECT
                    s.id AS service_id,
                    s.name AS service_name,
                    COUNT(DISTINCT mc.customer_id) AS consulted_customer_count
                FROM monthly_customers mc
                JOIN consultation c
                  ON c.customer_id = mc.customer_id
                 AND c.session_no = 1
                JOIN service s
                  ON s.id = c.consulted_service_id
                 AND s.store_id = mc.store_id
                GROUP BY s.id, s.name
                ORDER BY consulted_customer_count DESC, s.name ASC, s.id ASC
                """;

        return jdbcTemplate.query(
                sql,
                monthParams(storeId, monthStartInclusive, monthEndExclusive),
                (rs, rowNum) -> new ServiceConsultationCounts(
                        rs.getObject("service_id", UUID.class),
                        rs.getString("service_name"),
                        rs.getLong("consulted_customer_count")
                )
        );
    }

    public RegistrationCounts findRegistrationCounts(
            UUID storeId,
            LocalDate monthStartInclusive,
            LocalDate monthEndExclusive
    ) {
        String sql = monthlyCustomersCte() + """
                SELECT
                    COUNT(*) AS consulted_count,
                    COUNT(*) FILTER (WHERE status = 'REGISTERED') AS registered_count
                FROM monthly_customers
                """;

        return jdbcTemplate.queryForObject(
                sql,
                monthParams(storeId, monthStartInclusive, monthEndExclusive),
                (rs, rowNum) -> new RegistrationCounts(
                        rs.getLong("consulted_count"),
                        rs.getLong("registered_count")
                )
        );
    }

    public List<MonthlyRegistrationCounts> findMonthlyRegistrationCounts(
            UUID storeId,
            LocalDate periodStartInclusive,
            LocalDate periodEndExclusive
    ) {
        String sql = """
                WITH months AS (
                    SELECT generate_series(
                               CAST(:periodStartDate AS date),
                               (CAST(:periodEndDate AS date) - INTERVAL '1 month')::date,
                               INTERVAL '1 month'
                           )::date AS month_start
                ),
                monthly_customers AS (
                    SELECT
                        date_trunc('month', c.first_consult_at)::date AS month_start,
                        c.status
                    FROM customer c
                    WHERE c.store_id = :storeId
                      AND c.first_consult_at >= :periodStartDate
                      AND c.first_consult_at < :periodEndDate
                )
                SELECT
                    to_char(m.month_start, 'YYYY-MM') AS month,
                    COUNT(mc.month_start) AS consulted_count,
                    COUNT(mc.month_start) FILTER (WHERE mc.status = 'REGISTERED') AS registered_count
                FROM months m
                LEFT JOIN monthly_customers mc ON mc.month_start = m.month_start
                GROUP BY m.month_start
                ORDER BY m.month_start ASC
                """;

        MapSqlParameterSource params = periodParams(storeId, periodStartInclusive, periodEndExclusive);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new MonthlyRegistrationCounts(
                        rs.getString("month"),
                        rs.getLong("consulted_count"),
                        rs.getLong("registered_count")
                )
        );
    }

    public List<MonthlyServiceRegistrationCounts> findMonthlyServiceRegistrationCounts(
            UUID storeId,
            LocalDate periodStartInclusive,
            LocalDate periodEndExclusive
    ) {
        String sql = """
                WITH months AS (
                    SELECT generate_series(
                               CAST(:periodStartDate AS date),
                               (CAST(:periodEndDate AS date) - INTERVAL '1 month')::date,
                               INTERVAL '1 month'
                           )::date AS month_start
                ),
                service_scope AS (
                    SELECT DISTINCT
                        s.id AS service_id,
                        s.name AS service_name
                    FROM customer cu
                    JOIN consultation co
                      ON co.customer_id = cu.id
                     AND co.session_no = 1
                    JOIN service s
                      ON s.id = co.consulted_service_id
                     AND s.store_id = cu.store_id
                    WHERE cu.store_id = :storeId
                      AND cu.first_consult_at >= :periodStartDate
                      AND cu.first_consult_at < :periodEndDate
                ),
                monthly_service_customers AS (
                    SELECT
                        date_trunc('month', cu.first_consult_at)::date AS month_start,
                        s.id AS service_id,
                        cu.id AS customer_id,
                        cu.status
                    FROM customer cu
                    JOIN consultation co
                      ON co.customer_id = cu.id
                     AND co.session_no = 1
                    JOIN service s
                      ON s.id = co.consulted_service_id
                     AND s.store_id = cu.store_id
                    WHERE cu.store_id = :storeId
                      AND cu.first_consult_at >= :periodStartDate
                      AND cu.first_consult_at < :periodEndDate
                )
                SELECT
                    to_char(m.month_start, 'YYYY-MM') AS month,
                    ss.service_id,
                    ss.service_name,
                    COUNT(DISTINCT msc.customer_id) AS consulted_count,
                    COUNT(DISTINCT msc.customer_id) FILTER (
                        WHERE msc.status = 'REGISTERED'
                    ) AS registered_count
                FROM service_scope ss
                CROSS JOIN months m
                LEFT JOIN monthly_service_customers msc
                       ON msc.service_id = ss.service_id
                      AND msc.month_start = m.month_start
                GROUP BY m.month_start, ss.service_id, ss.service_name
                ORDER BY ss.service_name ASC, ss.service_id ASC, m.month_start ASC
                """;

        return jdbcTemplate.query(
                sql,
                periodParams(storeId, periodStartInclusive, periodEndExclusive),
                (rs, rowNum) -> new MonthlyServiceRegistrationCounts(
                        rs.getString("month"),
                        rs.getObject("service_id", UUID.class),
                        rs.getString("service_name"),
                        rs.getLong("consulted_count"),
                        rs.getLong("registered_count")
                )
        );
    }

    public List<InflowPathCounts> findInflowPathCounts(
            UUID storeId,
            LocalDate monthStartInclusive,
            LocalDate monthEndExclusive
    ) {
        String sql = """
                WITH monthly_customers AS (
                    SELECT
                        c.id AS customer_id,
                        c.store_id,
                        c.status,
                        c.inflow_path_id
                    FROM customer c
                    WHERE c.store_id = :storeId
                      AND c.first_consult_at >= :monthStartDate
                      AND c.first_consult_at < :monthEndDate
                ),
                inflow_path_scope AS (
                    SELECT
                        ipo.id AS inflow_path_id,
                        ipo.name AS inflow_path_name,
                        ipo.display_order
                    FROM inflow_path_option ipo
                    WHERE ipo.store_id = :storeId
                      AND ipo.is_active = TRUE

                    UNION

                    SELECT DISTINCT
                        ipo.id AS inflow_path_id,
                        ipo.name AS inflow_path_name,
                        ipo.display_order
                    FROM monthly_customers mc
                    JOIN inflow_path_option ipo
                      ON ipo.id = mc.inflow_path_id
                     AND ipo.store_id = mc.store_id
                )
                SELECT
                    ips.inflow_path_id,
                    ips.inflow_path_name,
                    COUNT(DISTINCT mc.customer_id) AS new_consultation_count,
                    COUNT(DISTINCT mc.customer_id) FILTER (
                        WHERE mc.status = 'REGISTERED'
                    ) AS registered_count
                FROM inflow_path_scope ips
                LEFT JOIN monthly_customers mc
                       ON mc.inflow_path_id = ips.inflow_path_id
                GROUP BY ips.inflow_path_id, ips.inflow_path_name, ips.display_order
                ORDER BY ips.display_order ASC, ips.inflow_path_name ASC, ips.inflow_path_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                monthParams(storeId, monthStartInclusive, monthEndExclusive),
                (rs, rowNum) -> new InflowPathCounts(
                        rs.getObject("inflow_path_id", UUID.class),
                        rs.getString("inflow_path_name"),
                        rs.getLong("new_consultation_count"),
                        rs.getLong("registered_count")
                )
        );
    }

    private String monthlyCustomersCte() {
        return """
                WITH monthly_customers AS (
                    SELECT
                        c.id AS customer_id,
                        c.store_id,
                        c.status,
                        c.inflow_path_id
                    FROM customer c
                    WHERE c.store_id = :storeId
                      AND c.first_consult_at >= :monthStartDate
                      AND c.first_consult_at < :monthEndDate
                )
                """;
    }

    private MapSqlParameterSource monthParams(
            UUID storeId,
            LocalDate monthStartInclusive,
            LocalDate monthEndExclusive
    ) {
        return new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("monthStartDate", monthStartInclusive)
                .addValue("monthEndDate", monthEndExclusive);
    }

    private MapSqlParameterSource periodParams(
            UUID storeId,
            LocalDate periodStartInclusive,
            LocalDate periodEndExclusive
    ) {
        return new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("periodStartDate", periodStartInclusive)
                .addValue("periodEndDate", periodEndExclusive);
    }

    public record SummaryCounts(
            long newConsultationCount,
            long newRegistrationCount,
            long nonRegisteredCount
    ) {
    }

    public record ServiceConsultationCounts(
            UUID serviceId,
            String serviceName,
            long consultedCustomerCount
    ) {
    }

    public record RegistrationCounts(
            long consultedCount,
            long registeredCount
    ) {
    }

    public record MonthlyRegistrationCounts(
            String month,
            long consultedCount,
            long registeredCount
    ) {
    }

    public record MonthlyServiceRegistrationCounts(
            String month,
            UUID serviceId,
            String serviceName,
            long consultedCount,
            long registeredCount
    ) {
    }

    public record InflowPathCounts(
            UUID inflowPathId,
            String inflowPathName,
            long newConsultationCount,
            long registeredCount
    ) {
    }
}
