package com.fitback.domain.analysisreport.repository;

import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.InflowPathCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.MonthlyRegistrationCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.MonthlyServiceRegistrationCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.ServiceConsultationCounts;
import com.fitback.domain.analysisreport.repository.AnalysisReportTotalQueryRepository.SummaryCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(properties = {
        "jwt.secret=integration-test-secret-key-that-is-at-least-thirty-two-bytes",
        "spring.mail.username=integration-test",
        "spring.mail.password=integration-test"
})
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class AnalysisReportTotalQueryRepositoryIntegrationTest {

    @Autowired
    private AnalysisReportTotalQueryRepository queryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID storeId;
    private UUID otherStoreId;
    private UUID userId;
    private UUID otherUserId;
    private UUID ptServiceId;
    private UUID spinServiceId;
    private UUID otherServiceId;
    private UUID walkInPathId;
    private UUID oldPathId;
    private UUID otherPathId;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        otherStoreId = UUID.randomUUID();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        ptServiceId = UUID.randomUUID();
        spinServiceId = UUID.randomUUID();
        otherServiceId = UUID.randomUUID();
        walkInPathId = UUID.randomUUID();
        oldPathId = UUID.randomUUID();
        otherPathId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-15T10:00:00+09:00");

        insertStore(storeId, "전체 리포트 테스트 매장", now);
        insertStore(otherStoreId, "타 매장", now);
        insertUser(userId, storeId, "total-report", now);
        insertUser(otherUserId, otherStoreId, "other-total-report", now);
        insertService(ptServiceId, storeId, "PT", true, now);
        insertService(spinServiceId, storeId, "스피닝", false, now);
        insertService(otherServiceId, otherStoreId, "타매장 PT", true, now);
        insertInflowPath(walkInPathId, storeId, "워크인", 1, true, now);
        insertInflowPath(oldPathId, storeId, "과거 경로", 9, false, now);
        insertInflowPath(otherPathId, otherStoreId, "타매장 경로", 1, true, now);

        UUID registeredPtCustomerId = insertCustomer(
                storeId,
                walkInPathId,
                "등록PT",
                "010-1000-0001",
                "REGISTERED",
                LocalDate.of(2026, 10, 3),
                now
        );
        insertConsultation(registeredPtCustomerId, userId, ptServiceId, 1,
                OffsetDateTime.parse("2026-10-03T10:00:00+09:00"));

        UUID pendingPtCustomerId = insertCustomer(
                storeId,
                oldPathId,
                "미등록PT",
                "010-1000-0002",
                "PENDING",
                LocalDate.of(2026, 10, 4),
                now
        );
        insertConsultation(pendingPtCustomerId, userId, ptServiceId, 1,
                OffsetDateTime.parse("2026-10-04T10:00:00+09:00"));

        UUID registeredInactiveServiceCustomerId = insertCustomer(
                storeId,
                oldPathId,
                "등록스피닝",
                "010-1000-0003",
                "REGISTERED",
                LocalDate.of(2026, 10, 5),
                now
        );
        insertConsultation(registeredInactiveServiceCustomerId, userId, spinServiceId, 1,
                OffsetDateTime.parse("2026-10-05T10:00:00+09:00"));

        UUID previousMonthCustomerId = insertCustomer(
                storeId,
                walkInPathId,
                "전월고객",
                "010-1000-0004",
                "REGISTERED",
                LocalDate.of(2026, 9, 5),
                now
        );
        insertConsultation(previousMonthCustomerId, userId, ptServiceId, 1,
                OffsetDateTime.parse("2026-09-05T10:00:00+09:00"));

        UUID otherStoreCustomerId = insertCustomer(
                otherStoreId,
                otherPathId,
                "타매장고객",
                "010-2000-0001",
                "REGISTERED",
                LocalDate.of(2026, 10, 6),
                now
        );
        insertConsultation(otherStoreCustomerId, otherUserId, otherServiceId, 1,
                OffsetDateTime.parse("2026-10-06T10:00:00+09:00"));
    }

    @Test
    @DisplayName("선택 월 summary와 종목별 상담 구성비 집계는 타 매장 데이터를 제외한다")
    void selectedMonthAggregatesExcludeOtherStores() {
        SummaryCounts summary = queryRepository.findSummaryCounts(
                storeId,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1)
        );
        List<ServiceConsultationCounts> serviceCounts = queryRepository.findServiceConsultationCounts(
                storeId,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1)
        );

        assertThat(summary.newConsultationCount()).isEqualTo(3);
        assertThat(summary.newRegistrationCount()).isEqualTo(2);
        assertThat(summary.nonRegisteredCount()).isEqualTo(1);
        assertThat(serviceCounts)
                .extracting("serviceId", "serviceName", "consultedCustomerCount")
                .containsExactly(
                        tuple(ptServiceId, "PT", 2L),
                        tuple(spinServiceId, "스피닝", 1L)
                );
    }

    @Test
    @DisplayName("최근 6개월 전체/종목별 전환율 집계는 빈 월과 비활성 종목 데이터를 포함한다")
    void trendAggregatesIncludeEmptyMonthsAndInactiveServiceWithData() {
        List<MonthlyRegistrationCounts> monthlyCounts = queryRepository.findMonthlyRegistrationCounts(
                storeId,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 11, 1)
        );
        List<MonthlyServiceRegistrationCounts> serviceMonthlyCounts =
                queryRepository.findMonthlyServiceRegistrationCounts(
                        storeId,
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 11, 1)
                );

        assertThat(monthlyCounts)
                .extracting("month", "consultedCount", "registeredCount")
                .containsExactly(
                        tuple("2026-05", 0L, 0L),
                        tuple("2026-06", 0L, 0L),
                        tuple("2026-07", 0L, 0L),
                        tuple("2026-08", 0L, 0L),
                        tuple("2026-09", 1L, 1L),
                        tuple("2026-10", 3L, 2L)
                );
        assertThat(serviceMonthlyCounts)
                .filteredOn(row -> row.serviceId().equals(spinServiceId))
                .extracting("month", "serviceName", "consultedCount", "registeredCount")
                .containsExactly(
                        tuple("2026-05", "스피닝", 0L, 0L),
                        tuple("2026-06", "스피닝", 0L, 0L),
                        tuple("2026-07", "스피닝", 0L, 0L),
                        tuple("2026-08", "스피닝", 0L, 0L),
                        tuple("2026-09", "스피닝", 0L, 0L),
                        tuple("2026-10", "스피닝", 1L, 1L)
                );
    }

    @Test
    @DisplayName("방문 경로 집계는 활성 0건 항목과 데이터가 있는 비활성 항목을 포함한다")
    void inflowPathAggregatesIncludeActiveAndInactiveWithData() {
        List<InflowPathCounts> counts = queryRepository.findInflowPathCounts(
                storeId,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1)
        );

        assertThat(counts)
                .extracting("inflowPathId", "inflowPathName", "newConsultationCount", "registeredCount")
                .containsExactly(
                        tuple(walkInPathId, "워크인", 1L, 1L),
                        tuple(oldPathId, "과거 경로", 2L, 1L)
                );
    }

    private void insertStore(UUID id, String name, OffsetDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO store (id, name, store_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, name, "GYM", timestamp(now), timestamp(now));
    }

    private void insertUser(UUID id, UUID storeId, String prefix, OffsetDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, store_id, email, nickname, role, password,
                    agree_marketing, agree_terms, email_verified, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, storeId, prefix + "-" + id + "@integration.test", prefix + "-" + id.toString().substring(0, 8),
                "STAFF", "password", false, true, true, timestamp(now), timestamp(now));
    }

    private void insertService(UUID id, UUID storeId, String name, boolean active, OffsetDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO service (
                    id, store_id, name, description, price, is_active, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, storeId, name, null, null, active, timestamp(now), timestamp(now));
    }

    private void insertInflowPath(
            UUID id,
            UUID storeId,
            String name,
            int displayOrder,
            boolean active,
            OffsetDateTime now
    ) {
        jdbcTemplate.update("""
                INSERT INTO inflow_path_option (
                    id, store_id, name, display_order, is_active, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, storeId, name, displayOrder, active, timestamp(now), timestamp(now));
    }

    private UUID insertCustomer(
            UUID storeId,
            UUID inflowPathId,
            String name,
            String phoneNumber,
            String status,
            LocalDate firstConsultAt,
            OffsetDateTime now
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO customer (
                    id, store_id, registered_service_id, name, gender, birth_date,
                    phone_num, preferred_contact_channel, inflow_path_id, status,
                    registered_at, first_consult_at, latest_consult_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                storeId,
                null,
                name,
                "FEMALE",
                Date.valueOf(LocalDate.of(2000, 1, 1)),
                phoneNumber,
                "KAKAO",
                inflowPathId,
                status,
                "REGISTERED".equals(status) ? timestamp(now) : null,
                Date.valueOf(firstConsultAt),
                Date.valueOf(firstConsultAt),
                timestamp(now),
                timestamp(now)
        );
        return id;
    }

    private void insertConsultation(
            UUID customerId,
            UUID userId,
            UUID serviceId,
            int sessionNo,
            OffsetDateTime consultedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO consultation (
                    id, customer_id, user_id, consulted_service_id, consulted_at,
                    session_no, stage, summary, ai_analysis_status, source_type,
                    raw_text, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                customerId,
                userId,
                serviceId,
                timestamp(consultedAt),
                sessionNo,
                "CONSULTATION",
                "상담 요약",
                "COMPLETED",
                "DIRECT",
                "상담 원문",
                timestamp(consultedAt),
                timestamp(consultedAt)
        );
    }

    private Timestamp timestamp(OffsetDateTime dateTime) {
        return Timestamp.from(dateTime.toInstant());
    }
}
