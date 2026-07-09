package com.fitback.domain.inquiry.repository;

import com.fitback.domain.inquiry.enums.InquiryStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jwt.secret=integration-test-secret-key-that-is-at-least-thirty-two-bytes",
        "spring.mail.username=integration-test",
        "spring.mail.password=integration-test"
})
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class InquiryConversionConcurrencyIntegrationTest {

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID storeId;
    private UUID userId;
    private UUID serviceId;
    private UUID inflowPathId;
    private UUID inquiryId;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        inflowPathId = UUID.randomUUID();
        inquiryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-09T20:00:00+09:00");
        Timestamp timestamp = Timestamp.from(now.toInstant());

        jdbcTemplate.update("""
                INSERT INTO store (id, name, store_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, storeId, "문의 동시성 테스트", "GYM", timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, store_id, email, nickname, role, password,
                    agree_marketing, agree_terms, email_verified, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId, storeId, userId + "@integration.test", "동시성상담자", "STAFF", "password",
                false, true, true, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO service (
                    id, store_id, name, description, price, is_active, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, serviceId, storeId, "PT", null, null, true, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO inflow_path_option (
                    id, store_id, name, display_order, is_active, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, inflowPathId, storeId, "워크인", 1, true, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO inquiry (
                    id, store_id, customer_id, service_id, user_id, name, gender,
                    birth_date, phone_num, preferred_contact_channel, inflow_path_id,
                    inquiry_status, inquired_at, visit_scheduled_at, raw_text,
                    converted_customer_id, converted_consultation_id, converted_at,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                inquiryId, storeId, null, serviceId, userId, "동시성고객", "FEMALE",
                Date.valueOf(LocalDate.of(2000, 1, 1)), "010-0000-0000", "KAKAO", inflowPathId,
                "RECEIVED", timestamp, null, "동시 전환 테스트", null, null, null,
                timestamp, timestamp);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM inquiry WHERE id = ?", inquiryId);
        jdbcTemplate.update("DELETE FROM inflow_path_option WHERE id = ?", inflowPathId);
        jdbcTemplate.update("DELETE FROM service WHERE id = ?", serviceId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        jdbcTemplate.update("DELETE FROM store WHERE id = ?", storeId);
    }

    @Test
    @DisplayName("동시 전환 조회는 첫 트랜잭션 커밋까지 대기한 뒤 CONVERTED 상태를 확인한다")
    void concurrentConversionWaitsForLockAndReadsConvertedStatus() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            Future<Void> first = executor.submit(() -> transactionTemplate.execute(status -> {
                inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId).orElseThrow();
                firstLockAcquired.countDown();
                await(allowFirstCommit);
                jdbcTemplate.update(
                        "UPDATE inquiry SET inquiry_status = 'CONVERTED' WHERE id = ?",
                        inquiryId
                );
                return null;
            }));

            assertThat(firstLockAcquired.await(3, TimeUnit.SECONDS)).isTrue();

            Future<InquiryStatus> second = executor.submit(() -> transactionTemplate.execute(status ->
                    inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId)
                            .orElseThrow()
                            .getInquiryStatus()
            ));

            Thread.sleep(200);
            assertThat(second.isDone()).isFalse();

            allowFirstCommit.countDown();

            first.get(3, TimeUnit.SECONDS);
            assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo(InquiryStatus.CONVERTED);
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release first transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release first transaction", e);
        }
    }
}
