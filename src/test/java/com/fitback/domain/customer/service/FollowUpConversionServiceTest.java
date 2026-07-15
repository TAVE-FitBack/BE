package com.fitback.domain.customer.service;

import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpConversion;
import com.fitback.domain.customer.entity.MessageTemplate;
import com.fitback.domain.customer.enums.ConversionSource;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.repository.FollowUpConversionRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.MessageTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpConversionServiceTest {

    @Mock
    private FollowUpConversionRepository followUpConversionRepository;

    @Mock
    private MessageTemplateRepository messageTemplateRepository;

    @Mock
    private FollowUpRepository followUpRepository;

    @InjectMocks
    private FollowUpConversionService followUpConversionService;

    @Test
    @DisplayName("이미 고객의 전환 귀속이 있으면 중복 저장하지 않는다")
    void recordConversionIfAbsentAlreadyExists() {
        Customer customer = customer(UUID.randomUUID());
        OffsetDateTime convertedAt = OffsetDateTime.now();
        when(followUpConversionRepository.existsByCustomer_Id(customer.getId())).thenReturn(true);

        boolean created = followUpConversionService.recordConversionIfAbsent(
                customer,
                convertedAt,
                ConversionSource.UNKNOWN
        );

        assertThat(created).isFalse();
        verify(followUpConversionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(messageTemplateRepository, followUpRepository);
    }

    @Test
    @DisplayName("등록 시각 이전 최신 전송 메시지가 있으면 FOLLOW_UP_MESSAGE로 귀속한다")
    void recordConversionWithLatestSentMessage() {
        Customer customer = customer(UUID.randomUUID());
        OffsetDateTime convertedAt = OffsetDateTime.now();
        FollowUp followUp = followUp(UUID.randomUUID(), customer, 2);
        MessageTemplate messageTemplate = messageTemplate(UUID.randomUUID(), customer, followUp, 2);
        when(followUpConversionRepository.existsByCustomer_Id(customer.getId())).thenReturn(false);
        when(messageTemplateRepository.findFirstByCustomerIdAndSentAtIsNotNullAndSentAtLessThanEqualOrderBySentAtDescIdDesc(
                customer.getId(),
                convertedAt
        )).thenReturn(Optional.of(messageTemplate));

        boolean created = followUpConversionService.recordConversionIfAbsent(
                customer,
                convertedAt,
                ConversionSource.UNKNOWN
        );

        FollowUpConversion saved = captureSavedConversion();
        assertThat(created).isTrue();
        assertThat(saved.getConversionSource()).isEqualTo(ConversionSource.FOLLOW_UP_MESSAGE);
        assertThat(saved.getFollowUp()).isSameAs(followUp);
        assertThat(saved.getMessageTemplate()).isSameAs(messageTemplate);
        assertThat(saved.getContactRound()).isEqualTo(2);
        assertThat(saved.getConvertedAt()).isEqualTo(convertedAt);
    }

    @Test
    @DisplayName("전송 메시지가 없고 PENDING follow_up이 있으면 MANUAL_STATUS_CHANGE로 귀속한다")
    void recordConversionWithActiveFollowUp() {
        Customer customer = customer(UUID.randomUUID());
        OffsetDateTime convertedAt = OffsetDateTime.now();
        FollowUp followUp = followUp(UUID.randomUUID(), customer, 3);
        when(followUpConversionRepository.existsByCustomer_Id(customer.getId())).thenReturn(false);
        when(messageTemplateRepository.findFirstByCustomerIdAndSentAtIsNotNullAndSentAtLessThanEqualOrderBySentAtDescIdDesc(
                customer.getId(),
                convertedAt
        )).thenReturn(Optional.empty());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(
                customer.getId(),
                FollowUpStatus.PENDING
        )).thenReturn(Optional.of(followUp));

        boolean created = followUpConversionService.recordConversionIfAbsent(
                customer,
                convertedAt,
                ConversionSource.UNKNOWN
        );

        FollowUpConversion saved = captureSavedConversion();
        assertThat(created).isTrue();
        assertThat(saved.getConversionSource()).isEqualTo(ConversionSource.MANUAL_STATUS_CHANGE);
        assertThat(saved.getFollowUp()).isSameAs(followUp);
        assertThat(saved.getMessageTemplate()).isNull();
        assertThat(saved.getContactRound()).isEqualTo(3);
    }

    @Test
    @DisplayName("메시지와 follow_up 귀속이 불가능하면 허용된 fallback 출처로 저장한다")
    void recordConversionWithFallbackSource() {
        Customer customer = customer(UUID.randomUUID());
        OffsetDateTime convertedAt = OffsetDateTime.now();
        when(followUpConversionRepository.existsByCustomer_Id(customer.getId())).thenReturn(false);
        when(messageTemplateRepository.findFirstByCustomerIdAndSentAtIsNotNullAndSentAtLessThanEqualOrderBySentAtDescIdDesc(
                customer.getId(),
                convertedAt
        )).thenReturn(Optional.empty());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(
                customer.getId(),
                FollowUpStatus.PENDING
        )).thenReturn(Optional.empty());
        when(followUpRepository.findFirstByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId()))
                .thenReturn(Optional.empty());

        boolean created = followUpConversionService.recordConversionIfAbsent(
                customer,
                convertedAt,
                ConversionSource.DIRECT_REGISTRATION
        );

        FollowUpConversion saved = captureSavedConversion();
        assertThat(created).isTrue();
        assertThat(saved.getConversionSource()).isEqualTo(ConversionSource.DIRECT_REGISTRATION);
        assertThat(saved.getFollowUp()).isNull();
        assertThat(saved.getMessageTemplate()).isNull();
        assertThat(saved.getContactRound()).isNull();
    }

    private FollowUpConversion captureSavedConversion() {
        ArgumentCaptor<FollowUpConversion> captor = ArgumentCaptor.forClass(FollowUpConversion.class);
        verify(followUpConversionRepository).save(captor.capture());
        return captor.getValue();
    }

    private Customer customer(UUID id) {
        return Customer.builder()
                .id(id)
                .build();
    }

    private FollowUp followUp(UUID id, Customer customer, int contactRound) {
        return FollowUp.builder()
                .id(id)
                .customer(customer)
                .status(FollowUpStatus.PENDING)
                .contactRound(contactRound)
                .build();
    }

    private MessageTemplate messageTemplate(UUID id, Customer customer, FollowUp followUp, Integer contactRound) {
        return MessageTemplate.builder()
                .id(id)
                .customer(customer)
                .followUp(followUp)
                .contactRound(contactRound)
                .build();
    }
}
