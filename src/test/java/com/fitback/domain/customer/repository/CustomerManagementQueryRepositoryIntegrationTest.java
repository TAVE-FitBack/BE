package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationPageRows;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationSearchCondition;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jwt.secret=integration-test-secret-key-that-is-at-least-thirty-two-bytes",
        "spring.mail.username=integration-test",
        "spring.mail.password=integration-test"
})
@Transactional
@Disabled("백엔드 작업 완료 후 PostgreSQL 통합 검증 시 실행")
class CustomerManagementQueryRepositoryIntegrationTest {

    @Autowired
    private CustomerManagementQueryRepository queryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID storeId;
    private UUID customerId;
    private UUID counselorId;
    private UUID latestServiceId;
    private UUID interestServiceId;
    private UUID inflowPathId;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        counselorId = UUID.randomUUID();
        latestServiceId = UUID.randomUUID();
        interestServiceId = UUID.randomUUID();
        inflowPathId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-01T09:00:00+09:00");

        jdbcTemplate.update("""
                INSERT INTO store (id, name, store_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, storeId, "통합테스트 매장", "GYM", Timestamp.from(now.toInstant()), Timestamp.from(now.toInstant()));
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, store_id, email, nickname, role, password,
                    agree_marketing, agree_terms, email_verified, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                counselorId,
                storeId,
                counselorId + "@integration.test",
                "상담자-" + counselorId.toString().substring(0, 8),
                "STAFF",
                "password",
                false,
                true,
                true,
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );
        insertService(latestServiceId, "PT", now);
        insertService(interestServiceId, "스피닝", now);
        jdbcTemplate.update("""
                INSERT INTO inflow_path_option (
                    id, store_id, name, display_order, is_active, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                inflowPathId,
                storeId,
                "네이버 예약",
                1,
                true,
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );
        jdbcTemplate.update("""
                INSERT INTO customer (
                    id, store_id, registered_service_id, name, gender, birth_date,
                    phone_num, preferred_contact_channel, inflow_path_id, status,
                    registered_at, first_consult_at, latest_consult_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                customerId,
                storeId,
                null,
                "김통합",
                "FEMALE",
                Date.valueOf(LocalDate.of(2000, 1, 1)),
                "010-9999-8888",
                "KAKAO",
                inflowPathId,
                "PENDING",
                null,
                Date.valueOf(LocalDate.of(2026, 10, 2)),
                Date.valueOf(LocalDate.of(2026, 10, 12)),
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );

        insertConsultation(1, "이전 상담 요약", "FIRST_FOLLOW_UP",
                OffsetDateTime.parse("2026-10-02T10:00:00+09:00"));
        UUID latestConsultationId = insertConsultation(2, "최신 상담 요약", "CONSULTATION",
                OffsetDateTime.parse("2026-10-12T14:00:00+09:00"));

        jdbcTemplate.update("""
                INSERT INTO interest_service (id, customer_id, service_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                customerId,
                interestServiceId,
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );
        jdbcTemplate.update("""
                INSERT INTO customer_ai_insight (
                    customer_id, lead_temperature, temperature_basis, priority_score,
                    analyzed_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                customerId,
                "WARM",
                "통합 테스트",
                70,
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );
        jdbcTemplate.update("""
                INSERT INTO non_conversion_reason (
                    id, customer_id, consultation_id, reason_type, role,
                    reason_basis, confidence, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                customerId,
                latestConsultationId,
                "PRICE_BURDEN",
                "PRIMARY",
                "가격 부담",
                "HIGH",
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );
        jdbcTemplate.update("""
                INSERT INTO follow_up (
                    id, customer_id, consultation_id, recommend_contact_date,
                    status, memo, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                customerId,
                latestConsultationId,
                Date.valueOf(LocalDate.of(2026, 10, 15)),
                "PENDING",
                "목록 조회에 포함되면 안 됨",
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );
    }

    @Test
    @DisplayName("PostgreSQL에서 고객별 session_no가 가장 큰 상담 한 행만 조회한다")
    void findLatestConsultationPerCustomer() {
        ConsultationSearchCondition condition = new ConsultationSearchCondition(
                null, null, null, null, null, null, null, null, null, null
        );

        ConsultationPageRows result = queryRepository.findConsultations(
                storeId,
                CustomerManagementQueryValidator.resolveMonthRange("2026-10"),
                condition,
                0,
                10
        );

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement().satisfies(row -> {
            assertThat(row.customerId()).isEqualTo(customerId);
            assertThat(row.summary()).isEqualTo("최신 상담 요약");
            assertThat(row.stage()).isEqualTo("CONSULTATION");
            assertThat(row.leadTemperature()).isEqualTo("WARM");
            assertThat(row.counselorId()).isEqualTo(counselorId);
            assertThat(row.latestConsultAt())
                    .isEqualTo(OffsetDateTime.parse("2026-10-12T14:00:00+09:00"));
        });

        assertThat(queryRepository.findNonConversionReasons(List.of(customerId)))
                .singleElement()
                .satisfies(reason -> assertThat(reason.reasonType()).isEqualTo("PRICE_BURDEN"));
    }

    @Test
    @DisplayName("검색과 상담 목록 필터를 PostgreSQL 조회 조건에 모두 적용한다")
    void applySearchAndFilters() {
        ConsultationSearchCondition condition = new ConsultationSearchCondition(
                "스피닝",
                "FEMALE",
                interestServiceId,
                inflowPathId,
                "CONSULTATION",
                "PRICE_BURDEN",
                "PENDING",
                "WARM",
                null,
                counselorId
        );

        ConsultationPageRows result = queryRepository.findConsultations(
                storeId,
                CustomerManagementQueryValidator.resolveMonthRange("2026-10"),
                condition,
                0,
                10
        );

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement()
                .extracting(CustomerManagementQueryRepository.ConsultationRow::customerId)
                .isEqualTo(customerId);
    }

    private void insertService(UUID serviceId, String name, OffsetDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO service (
                    id, store_id, name, description, price, is_active, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                serviceId,
                storeId,
                name,
                null,
                null,
                true,
                Timestamp.from(now.toInstant()),
                Timestamp.from(now.toInstant())
        );
    }

    private UUID insertConsultation(
            int sessionNo,
            String summary,
            String stage,
            OffsetDateTime consultedAt
    ) {
        UUID consultationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO consultation (
                    id, customer_id, user_id, consulted_service_id, consulted_at,
                    session_no, stage, summary, source_type,
                    raw_text, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                consultationId,
                customerId,
                counselorId,
                latestServiceId,
                Timestamp.from(consultedAt.toInstant()),
                sessionNo,
                stage,
                summary,
                "DIRECT",
                "상담 원문",
                Timestamp.from(consultedAt.toInstant()),
                Timestamp.from(consultedAt.toInstant())
        );
        return consultationId;
    }
}
