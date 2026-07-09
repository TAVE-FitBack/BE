package com.fitback.domain.customer.entity;

import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.service.entity.Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Test
    @DisplayName("재등록하더라도 최초 등록 시각을 덮어쓰지 않는다")
    void markRegisteredKeepsFirstRegisteredAt() {
        OffsetDateTime firstRegisteredAt = OffsetDateTime.parse("2026-07-01T10:00:00+09:00");
        OffsetDateTime secondRegisteredAt = OffsetDateTime.parse("2026-07-10T15:00:00+09:00");
        Service firstService = Service.builder().name("PT").build();
        Service secondService = Service.builder().name("스피닝").build();
        Customer customer = Customer.builder()
                .status(CustomerStatus.PENDING)
                .build();

        customer.markRegistered(firstService, firstRegisteredAt);
        customer.markStatus(CustomerStatus.PENDING);
        customer.markRegistered(secondService, secondRegisteredAt);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.REGISTERED);
        assertThat(customer.getRegisteredService()).isEqualTo(secondService);
        assertThat(customer.getRegisteredAt()).isEqualTo(firstRegisteredAt);
    }
}
