package com.fitback.domain.customer.repository;

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

    public List<BoardRow> findBoardRows(UUID storeId, BoardCondition condition, LocalDate today) {
        String dateCondition = switch (condition.tab()) {
            case "TODAY" -> " AND f.recommend_contact_date = :today\n";
            case "SCHEDULED" -> " AND f.recommend_contact_date > :today\n";
            default -> throw new IllegalArgumentException("Unsupported follow-up board tab.");
        };
        String orderBy = switch (condition.tab()) {
            case "TODAY" -> """
                    ORDER BY cai.priority_score DESC NULLS LAST,
                             c.latest_consult_at DESC,
                             f.created_at DESC
                    """;
            case "SCHEDULED" -> """
                    ORDER BY f.recommend_contact_date ASC,
                             cai.priority_score DESC NULLS LAST,
                             c.latest_consult_at DESC,
                             f.created_at DESC
                    """;
            default -> throw new IllegalArgumentException("Unsupported follow-up board tab.");
        };

        StringBuilder filters = new StringBuilder(dateCondition);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("today", today);
        appendBoardFilters(filters, params, condition);

        String sql = """
                SELECT f.id AS follow_up_id,
                       c.id AS customer_id,
                       c.name AS customer_name,
                       c.phone_num,
                       c.gender,
                       COALESCE(registered_service.name, consulted_service.name, interest_service.name) AS service_name,
                       c.status AS customer_status,
                       f.status AS follow_up_status,
                       f.contact_round,
                       f.recommend_contact_date,
                       f.memo,
                       f.has_reply,
                       f.replied_at,
                       cai.lead_temperature,
                       cai.priority_score,
                       fai.action_basis ->> 'title' AS next_best_action_title,
                       fai.action_basis ->> 'description' AS next_best_action_description,
                       latest_message.id AS latest_message_template_id,
                       latest_message.delivery_status AS latest_message_delivery_status,
                       latest_message.generated_at AS latest_message_generated_at
                FROM follow_up f
                JOIN customer c ON c.id = f.customer_id
                JOIN consultation co ON co.id = f.consultation_id
                JOIN service consulted_service ON consulted_service.id = co.consulted_service_id
                LEFT JOIN service registered_service ON registered_service.id = c.registered_service_id
                LEFT JOIN LATERAL (
                    SELECT s.name
                    FROM interest_service isv
                    JOIN service s ON s.id = isv.service_id
                    WHERE isv.customer_id = c.id
                    ORDER BY isv.created_at DESC, isv.id DESC
                    LIMIT 1
                ) interest_service ON TRUE
                LEFT JOIN customer_ai_insight cai ON cai.customer_id = c.id
                LEFT JOIN follow_up_ai_insight fai ON fai.follow_up_id = f.id
                LEFT JOIN LATERAL (
                    SELECT mt.id,
                           mt.delivery_status,
                           mt.generated_at
                    FROM message_template mt
                    WHERE mt.follow_up_id = f.id
                    ORDER BY mt.generated_at DESC, mt.id DESC
                    LIMIT 1
                ) latest_message ON TRUE
                WHERE c.store_id = :storeId
                  AND f.status = 'PENDING'
                """ + filters + orderBy;

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new BoardRow(
                        rs.getObject("follow_up_id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("customer_name"),
                        rs.getString("phone_num"),
                        rs.getString("gender"),
                        rs.getString("service_name"),
                        rs.getString("customer_status"),
                        rs.getString("follow_up_status"),
                        rs.getInt("contact_round"),
                        rs.getObject("recommend_contact_date", LocalDate.class),
                        rs.getString("memo"),
                        rs.getBoolean("has_reply"),
                        rs.getObject("replied_at", OffsetDateTime.class),
                        rs.getString("lead_temperature"),
                        rs.getObject("priority_score", Integer.class),
                        rs.getString("next_best_action_title"),
                        rs.getString("next_best_action_description"),
                        rs.getObject("latest_message_template_id", UUID.class),
                        rs.getString("latest_message_delivery_status"),
                        rs.getObject("latest_message_generated_at", OffsetDateTime.class)
                )
        );
    }

    public List<NonConversionReasonRow> findNonConversionReasons(Collection<UUID> customerIds) {
        if (customerIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT customer_id, reason_type
                FROM non_conversion_reason
                WHERE customer_id IN (:customerIds)
                ORDER BY customer_id,
                         CASE WHEN role = 'PRIMARY' THEN 0 ELSE 1 END,
                         updated_at DESC,
                         id
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

    public EndedPageRows findEndedRows(
            UUID storeId,
            EndedCondition condition,
            int page,
            int size
    ) {
        StringBuilder filters = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("startDate", condition.startDate())
                .addValue("endDate", condition.endDate());
        appendEndedFilters(filters, params, condition);

        String baseSql = """
                WITH ended_base AS (
                    SELECT f.id AS follow_up_id,
                           c.id AS customer_id,
                           c.name AS customer_name,
                           c.phone_num,
                           c.gender,
                           COALESCE(registered_service.name, consulted_service.name, interest_service.name) AS service_name,
                           c.status AS customer_status,
                           f.status AS follow_up_status,
                           f.contact_round,
                           f.has_reply,
                           f.replied_at,
                           latest_message.id AS latest_message_template_id,
                           latest_message.delivery_status AS latest_message_delivery_status,
                           COALESCE(
                               latest_message.sent_at,
                               latest_message.delivered_at,
                               f.updated_at
                           ) AS latest_contact_at,
                           f.memo,
                           (
                               f.status = 'COMPLETED'
                               AND f.contact_round = 3
                               AND NOT EXISTS (
                                   SELECT 1
                                   FROM follow_up pending_f
                                   WHERE pending_f.customer_id = c.id
                                     AND pending_f.status = 'PENDING'
                               )
                           ) AS follow_up_completed,
                           f.updated_at AS follow_up_updated_at,
                           f.created_at AS follow_up_created_at
                    FROM follow_up f
                    JOIN customer c ON c.id = f.customer_id
                    JOIN consultation co ON co.id = f.consultation_id
                    JOIN service consulted_service ON consulted_service.id = co.consulted_service_id
                    LEFT JOIN service registered_service ON registered_service.id = c.registered_service_id
                    LEFT JOIN LATERAL (
                        SELECT s.name
                        FROM interest_service isv
                        JOIN service s ON s.id = isv.service_id
                        WHERE isv.customer_id = c.id
                        ORDER BY isv.created_at DESC, isv.id DESC
                        LIMIT 1
                    ) interest_service ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT mt.id,
                               mt.delivery_status,
                               mt.sent_at,
                               mt.delivered_at,
                               mt.generated_at
                        FROM message_template mt
                        WHERE mt.follow_up_id = f.id
                        ORDER BY mt.generated_at DESC, mt.id DESC
                        LIMIT 1
                    ) latest_message ON TRUE
                    WHERE c.store_id = :storeId
                      AND f.status IN ('COMPLETED', 'CLOSED')
                """ + filters + """
                )
                """;
        String dateFilter = """
                WHERE (:startDate IS NULL OR latest_contact_at::date >= :startDate)
                  AND (:endDate IS NULL OR latest_contact_at::date <= :endDate)
                """;

        Long totalElements = jdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM ended_base\n" + dateFilter,
                params,
                Long.class
        );

        params.addValue("limit", size)
                .addValue("offset", (long) page * size);

        String contentSql = baseSql + """
                SELECT follow_up_id,
                       customer_id,
                       customer_name,
                       phone_num,
                       gender,
                       service_name,
                       customer_status,
                       follow_up_status,
                       contact_round,
                       has_reply,
                       replied_at,
                       latest_message_template_id,
                       latest_message_delivery_status,
                       latest_contact_at,
                       memo,
                       follow_up_completed
                FROM ended_base
                """ + dateFilter + """
                ORDER BY latest_contact_at DESC NULLS LAST,
                         follow_up_updated_at DESC,
                         follow_up_created_at DESC
                LIMIT :limit OFFSET :offset
                """;

        List<EndedRow> content = jdbcTemplate.query(
                contentSql,
                params,
                (rs, rowNum) -> new EndedRow(
                        rs.getObject("follow_up_id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("customer_name"),
                        rs.getString("phone_num"),
                        rs.getString("gender"),
                        rs.getString("service_name"),
                        rs.getString("customer_status"),
                        rs.getString("follow_up_status"),
                        rs.getInt("contact_round"),
                        rs.getBoolean("has_reply"),
                        rs.getObject("replied_at", OffsetDateTime.class),
                        rs.getObject("latest_message_template_id", UUID.class),
                        rs.getString("latest_message_delivery_status"),
                        rs.getObject("latest_contact_at", OffsetDateTime.class),
                        rs.getString("memo"),
                        rs.getBoolean("follow_up_completed")
                )
        );

        return new EndedPageRows(content, totalElements == null ? 0 : totalElements);
    }

    private void appendBoardFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            BoardCondition condition
    ) {
        if (condition.keyword() != null) {
            sql.append("""
                      AND (
                          LOWER(c.name) LIKE :keyword
                          OR LOWER(c.phone_num) LIKE :keyword
                      )
                    """);
            params.addValue("keyword", "%" + condition.keyword().toLowerCase(Locale.ROOT) + "%");
        }
        if (condition.contactRound() != null) {
            sql.append("  AND f.contact_round = :contactRound\n");
            params.addValue("contactRound", condition.contactRound());
        }
        if (condition.hasReply() != null) {
            sql.append("  AND f.has_reply = :hasReply\n");
            params.addValue("hasReply", condition.hasReply());
        }
    }

    private void appendEndedFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            EndedCondition condition
    ) {
        if (condition.keyword() != null) {
            sql.append("""
                      AND (
                          LOWER(c.name) LIKE :keyword
                          OR LOWER(c.phone_num) LIKE :keyword
                      )
                    """);
            params.addValue("keyword", "%" + condition.keyword().toLowerCase(Locale.ROOT) + "%");
        }
        if (condition.followUpStatus() != null) {
            sql.append("  AND f.status = :followUpStatus\n");
            params.addValue("followUpStatus", condition.followUpStatus());
        }
        if (condition.contactRound() != null) {
            sql.append("  AND f.contact_round = :contactRound\n");
            params.addValue("contactRound", condition.contactRound());
        }
        if (condition.hasReply() != null) {
            sql.append("  AND f.has_reply = :hasReply\n");
            params.addValue("hasReply", condition.hasReply());
        }
    }

    public record SummaryCounts(
            long todayPendingCount,
            long secondRoundPendingCount,
            long overdueCount,
            long thisMonthPendingCount
    ) {
    }

    public record BoardCondition(
            String tab,
            String keyword,
            Integer contactRound,
            Boolean hasReply
    ) {
    }

    public record BoardRow(
            UUID followUpId,
            UUID customerId,
            String customerName,
            String phoneNum,
            String gender,
            String serviceName,
            String customerStatus,
            String followUpStatus,
            int contactRound,
            LocalDate recommendContactDate,
            String memo,
            boolean hasReply,
            OffsetDateTime repliedAt,
            String leadTemperature,
            Integer priorityScore,
            String nextBestActionTitle,
            String nextBestActionDescription,
            UUID latestMessageTemplateId,
            String latestMessageDeliveryStatus,
            OffsetDateTime latestMessageGeneratedAt
    ) {
    }

    public record NonConversionReasonRow(
            UUID customerId,
            String reasonType
    ) {
    }

    public record EndedCondition(
            String keyword,
            Integer contactRound,
            Boolean hasReply,
            String followUpStatus,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record EndedPageRows(
            List<EndedRow> content,
            long totalElements
    ) {
    }

    public record EndedRow(
            UUID followUpId,
            UUID customerId,
            String customerName,
            String phoneNum,
            String gender,
            String serviceName,
            String customerStatus,
            String followUpStatus,
            int contactRound,
            boolean hasReply,
            OffsetDateTime repliedAt,
            UUID latestMessageTemplateId,
            String latestMessageDeliveryStatus,
            OffsetDateTime latestContactAt,
            String memo,
            boolean followUpCompleted
    ) {
    }
}
