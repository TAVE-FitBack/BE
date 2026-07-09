package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.support.CustomerManagementQueryValidator.MonthRange;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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

    public ConsultationPageRows findConsultations(
            UUID storeId,
            MonthRange range,
            ConsultationSearchCondition condition,
            int page,
            int size
    ) {
        String cte = """
                WITH latest_consultation AS (
                    SELECT DISTINCT ON (co.customer_id)
                           co.id,
                           co.customer_id,
                           co.user_id,
                           co.consulted_service_id,
                           co.consulted_at,
                           co.session_no,
                           co.stage,
                           co.summary
                    FROM consultation co
                    JOIN customer c ON c.id = co.customer_id
                    WHERE c.store_id = :storeId
                      AND co.consulted_at >= :startAt
                      AND co.consulted_at < :endAt
                    ORDER BY co.customer_id, co.session_no DESC, co.id DESC
                )
                """;

        String fromAndWhere = """
                FROM latest_consultation lc
                JOIN customer c ON c.id = lc.customer_id
                JOIN service s ON s.id = lc.consulted_service_id
                JOIN inflow_path_option ip ON ip.id = c.inflow_path_id
                JOIN users u ON u.id = lc.user_id
                LEFT JOIN customer_ai_insight cai ON cai.customer_id = c.id
                WHERE c.store_id = :storeId
                """;

        StringBuilder filters = new StringBuilder();
        MapSqlParameterSource params = parameters(storeId, range);
        appendConsultationFilters(filters, params, condition);

        String countSql = cte + "SELECT COUNT(*) " + fromAndWhere + filters;
        Long totalElements = jdbcTemplate.queryForObject(countSql, params, Long.class);

        params.addValue("limit", size)
                .addValue("offset", (long) page * size);
        String contentSql = cte + """
                SELECT c.id AS customer_id,
                       c.name,
                       c.phone_num,
                       c.gender,
                       c.birth_date,
                       s.id AS service_id,
                       s.name AS service_name,
                       ip.id AS inflow_path_id,
                       ip.name AS inflow_path_name,
                       lc.stage,
                       lc.summary,
                       cai.lead_temperature,
                       c.status AS customer_status,
                       lc.consulted_at,
                       u.id AS counselor_id,
                       u.nickname AS counselor_name
                """ + fromAndWhere + filters + """
                ORDER BY lc.consulted_at DESC, lc.session_no DESC, c.id ASC
                LIMIT :limit OFFSET :offset
                """;

        List<ConsultationRow> content = jdbcTemplate.query(
                contentSql,
                params,
                (rs, rowNum) -> new ConsultationRow(
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("phone_num"),
                        rs.getString("gender"),
                        rs.getObject("birth_date", LocalDate.class),
                        rs.getObject("service_id", UUID.class),
                        rs.getString("service_name"),
                        rs.getObject("inflow_path_id", UUID.class),
                        rs.getString("inflow_path_name"),
                        rs.getString("stage"),
                        rs.getString("summary"),
                        rs.getString("lead_temperature"),
                        rs.getString("customer_status"),
                        rs.getObject("consulted_at", OffsetDateTime.class),
                        rs.getObject("counselor_id", UUID.class),
                        rs.getString("counselor_name")
                )
        );

        return new ConsultationPageRows(content, totalElements == null ? 0 : totalElements);
    }

    public List<NonConversionReasonRow> findNonConversionReasons(Collection<UUID> customerIds) {
        if (customerIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT customer_id, reason_type
                FROM non_conversion_reason
                WHERE customer_id IN (:customerIds)
                ORDER BY customer_id, CASE WHEN role = 'PRIMARY' THEN 0 ELSE 1 END, updated_at DESC, id
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("customerIds", customerIds),
                (rs, rowNum) -> new NonConversionReasonRow(
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("reason_type")
                )
        );
    }

    public InquiryPageRows findInquiries(
            UUID storeId,
            MonthRange range,
            InquirySearchCondition condition,
            int page,
            int size
    ) {
        String fromAndWhere = """
                FROM inquiry i
                JOIN service s ON s.id = i.service_id
                JOIN inflow_path_option ip ON ip.id = i.inflow_path_id
                JOIN users u ON u.id = i.user_id
                WHERE i.store_id = :storeId
                  AND i.inquired_at >= :startAt
                  AND i.inquired_at < :endAt
                  AND i.inquiry_status <> 'CONVERTED'
                """;
        StringBuilder filters = new StringBuilder();
        MapSqlParameterSource params = parameters(storeId, range);
        appendInquiryFilters(filters, params, condition);

        String countSql = "SELECT COUNT(*) " + fromAndWhere + filters;
        Long totalElements = jdbcTemplate.queryForObject(countSql, params, Long.class);

        params.addValue("limit", size)
                .addValue("offset", (long) page * size);
        String contentSql = """
                SELECT i.id AS inquiry_id,
                       i.name,
                       i.phone_num,
                       i.gender,
                       i.birth_date,
                       s.id AS service_id,
                       s.name AS service_name,
                       ip.id AS inflow_path_id,
                       ip.name AS inflow_path_name,
                       i.raw_text,
                       i.inquiry_status,
                       i.inquired_at,
                       i.visit_scheduled_at,
                       u.id AS counselor_id,
                       u.nickname AS counselor_name,
                       i.converted_customer_id,
                       i.converted_consultation_id
                """ + fromAndWhere + filters + """
                ORDER BY i.inquired_at DESC, i.id ASC
                LIMIT :limit OFFSET :offset
                """;

        List<InquiryRow> content = jdbcTemplate.query(
                contentSql,
                params,
                (rs, rowNum) -> new InquiryRow(
                        rs.getObject("inquiry_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("phone_num"),
                        rs.getString("gender"),
                        rs.getObject("birth_date", LocalDate.class),
                        rs.getObject("service_id", UUID.class),
                        rs.getString("service_name"),
                        rs.getObject("inflow_path_id", UUID.class),
                        rs.getString("inflow_path_name"),
                        rs.getString("raw_text"),
                        rs.getString("inquiry_status"),
                        rs.getObject("inquired_at", OffsetDateTime.class),
                        rs.getObject("visit_scheduled_at", OffsetDateTime.class),
                        rs.getObject("counselor_id", UUID.class),
                        rs.getString("counselor_name"),
                        rs.getObject("converted_customer_id", UUID.class),
                        rs.getObject("converted_consultation_id", UUID.class)
                )
        );

        return new InquiryPageRows(content, totalElements == null ? 0 : totalElements);
    }

    private void appendConsultationFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            ConsultationSearchCondition condition
    ) {
        if (condition.keyword() != null) {
            sql.append("""
                      AND (
                          LOWER(c.name) LIKE :keyword
                          OR LOWER(c.phone_num) LIKE :keyword
                          OR LOWER(s.name) LIKE :keyword
                          OR EXISTS (
                              SELECT 1
                              FROM interest_service i
                              JOIN service interest_s ON interest_s.id = i.service_id
                              WHERE i.customer_id = c.id
                                AND LOWER(interest_s.name) LIKE :keyword
                          )
                      )
                    """);
            params.addValue("keyword", "%" + condition.keyword().toLowerCase(Locale.ROOT) + "%");
        }
        appendEquals(sql, params, "c.gender", "gender", condition.gender());
        appendEquals(sql, params, "c.inflow_path_id", "inflowPathId", condition.inflowPathId());
        appendEquals(sql, params, "lc.stage", "stage", condition.stage());
        appendEquals(sql, params, "c.status", "status", condition.status());
        appendEquals(sql, params, "cai.lead_temperature", "leadTemperature", condition.leadTemperature());
        appendEquals(sql, params, "lc.user_id", "counselorId", condition.counselorId());

        if (condition.serviceId() != null) {
            sql.append("""
                      AND (
                          lc.consulted_service_id = :serviceId
                          OR EXISTS (
                              SELECT 1
                              FROM interest_service i
                              WHERE i.customer_id = c.id
                                AND i.service_id = :serviceId
                          )
                      )
                    """);
            params.addValue("serviceId", condition.serviceId());
        }
        if (condition.reasonType() != null) {
            sql.append("""
                      AND EXISTS (
                          SELECT 1
                          FROM non_conversion_reason ncr
                          WHERE ncr.customer_id = c.id
                            AND ncr.reason_type = :reasonType
                      )
                    """);
            params.addValue("reasonType", condition.reasonType());
        }
    }

    private void appendInquiryFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            InquirySearchCondition condition
    ) {
        if (condition.keyword() != null) {
            sql.append("""
                      AND (
                          LOWER(i.name) LIKE :keyword
                          OR LOWER(i.phone_num) LIKE :keyword
                          OR LOWER(s.name) LIKE :keyword
                      )
                    """);
            params.addValue("keyword", "%" + condition.keyword().toLowerCase(Locale.ROOT) + "%");
        }
        appendEquals(sql, params, "i.gender", "gender", condition.gender());
        appendEquals(sql, params, "i.service_id", "serviceId", condition.serviceId());
        appendEquals(sql, params, "i.inflow_path_id", "inflowPathId", condition.inflowPathId());
        appendEquals(sql, params, "i.inquiry_status", "inquiryStatus", condition.inquiryStatus());
        appendEquals(sql, params, "i.user_id", "counselorId", condition.counselorId());
    }

    private void appendEquals(
            StringBuilder sql,
            MapSqlParameterSource params,
            String column,
            String parameterName,
            Object value
    ) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = :").append(parameterName).append('\n');
            params.addValue(parameterName, value);
        }
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

    public record ConsultationSearchCondition(
            String keyword,
            String gender,
            UUID serviceId,
            UUID inflowPathId,
            String stage,
            String reasonType,
            String status,
            String leadTemperature,
            UUID counselorId
    ) {
    }

    public record ConsultationPageRows(
            List<ConsultationRow> content,
            long totalElements
    ) {
    }

    public record ConsultationRow(
            UUID customerId,
            String name,
            String phoneNum,
            String gender,
            LocalDate birthDate,
            UUID serviceId,
            String serviceName,
            UUID inflowPathId,
            String inflowPathName,
            String stage,
            String summary,
            String leadTemperature,
            String customerStatus,
            OffsetDateTime latestConsultAt,
            UUID counselorId,
            String counselorName
    ) {
    }

    public record NonConversionReasonRow(
            UUID customerId,
            String reasonType
    ) {
    }

    public record InquirySearchCondition(
            String keyword,
            String gender,
            UUID serviceId,
            UUID inflowPathId,
            String inquiryStatus,
            UUID counselorId
    ) {
    }

    public record InquiryPageRows(
            List<InquiryRow> content,
            long totalElements
    ) {
    }

    public record InquiryRow(
            UUID inquiryId,
            String name,
            String phoneNum,
            String gender,
            LocalDate birthDate,
            UUID serviceId,
            String serviceName,
            UUID inflowPathId,
            String inflowPathName,
            String rawText,
            String inquiryStatus,
            OffsetDateTime inquiredAt,
            OffsetDateTime visitScheduledAt,
            UUID counselorId,
            String counselorName,
            UUID convertedCustomerId,
            UUID convertedConsultationId
    ) {
    }
}
