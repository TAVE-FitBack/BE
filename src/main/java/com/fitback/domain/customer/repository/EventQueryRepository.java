package com.fitback.domain.customer.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EventQueryRepository {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<EventOptionRow> findActiveEventsByStoreId(UUID storeId) {
        String sql = """
                SELECT id, title, event_type, description, discount_rate, start_date, end_date
                FROM event
                WHERE store_id = :storeId
                  AND status = :status
                ORDER BY end_date ASC, start_date ASC, title ASC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("status", ACTIVE_STATUS);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new EventOptionRow(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("event_type"),
                rs.getString("description"),
                rs.getBigDecimal("discount_rate"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class)
        ));
    }

    public Optional<EventOptionRow> findActiveEventByIdAndStoreId(UUID eventId, UUID storeId) {
        String sql = """
                SELECT id, title, event_type, description, discount_rate, start_date, end_date
                FROM event
                WHERE id = :eventId
                  AND store_id = :storeId
                  AND status = :status
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("storeId", storeId)
                .addValue("status", ACTIVE_STATUS);

        List<EventOptionRow> events = jdbcTemplate.query(sql, params, (rs, rowNum) -> new EventOptionRow(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("event_type"),
                rs.getString("description"),
                rs.getBigDecimal("discount_rate"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class)
        ));
        return events.stream().findFirst();
    }

    public record EventOptionRow(
            UUID eventId,
            String title,
            String eventType,
            String description,
            BigDecimal discountRate,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }
}
