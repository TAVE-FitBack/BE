package com.fitback.domain.inquiry.repository;

import com.fitback.domain.customer.repository.CustomerRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InquiryConversionLockTest {

    @Test
    @DisplayName("문의 전환 조회는 비관적 쓰기 잠금을 사용한다")
    void inquiryConversionUsesPessimisticWriteLock() throws NoSuchMethodException {
        Method method = InquiryRepository.class.getMethod(
                "findByIdAndStoreIdForUpdate",
                UUID.class,
                UUID.class
        );

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("기존 고객 회차 계산 조회는 비관적 쓰기 잠금을 사용한다")
    void existingCustomerConversionUsesPessimisticWriteLock() throws NoSuchMethodException {
        Method method = CustomerRepository.class.getMethod(
                "findByPhoneNumAndStoreIdForUpdate",
                String.class,
                UUID.class
        );

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
