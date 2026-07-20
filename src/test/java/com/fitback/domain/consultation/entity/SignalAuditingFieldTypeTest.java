package com.fitback.domain.consultation.entity;

import com.fitback.domain.inquiry.entity.InquirySignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SignalAuditingFieldTypeTest {

    @Test
    @DisplayName("ConsultationSignal createdAt은 JPA Auditing 정책에 맞게 LocalDateTime을 사용한다")
    void consultationSignalCreatedAtUsesLocalDateTime() throws NoSuchFieldException {
        Field createdAt = ConsultationSignal.class.getDeclaredField("createdAt");

        assertThat(createdAt.getType()).isEqualTo(LocalDateTime.class);
        assertThat(createdAt.isAnnotationPresent(CreatedDate.class)).isTrue();
    }

    @Test
    @DisplayName("InquirySignal createdAt은 JPA Auditing 정책에 맞게 LocalDateTime을 사용한다")
    void inquirySignalCreatedAtUsesLocalDateTime() throws NoSuchFieldException {
        Field createdAt = InquirySignal.class.getDeclaredField("createdAt");

        assertThat(createdAt.getType()).isEqualTo(LocalDateTime.class);
        assertThat(createdAt.isAnnotationPresent(CreatedDate.class)).isTrue();
    }
}
