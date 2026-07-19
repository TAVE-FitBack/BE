package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewItemRequest;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.entity.ConsultationSignal;
import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import com.fitback.domain.consultation.repository.ConsultationSignalRepository;
import com.fitback.domain.inquiry.entity.Inquiry;
import com.fitback.domain.inquiry.entity.InquirySignal;
import com.fitback.domain.inquiry.repository.InquirySignalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationSignalServiceTest {

    @Mock
    private ConsultationSignalRepository consultationSignalRepository;

    @Mock
    private InquirySignalRepository inquirySignalRepository;

    @InjectMocks
    private ConsultationSignalService consultationSignalService;

    @Test
    void saveSnapshotReturnsEmptyWhenSnapshotIsNull() {
        Consultation consultation = Consultation.builder()
                .id(UUID.randomUUID())
                .build();

        List<ConsultationSignal> result = consultationSignalService.saveSnapshot(consultation, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(consultationSignalRepository, inquirySignalRepository);
    }

    @Test
    void saveSnapshotMapsPreviewItemsToConsultationSignals() {
        Consultation consultation = Consultation.builder()
                .id(UUID.randomUUID())
                .build();
        AiCheckPreviewSnapshotRequest snapshot = snapshot(
                item(AiCheckSignalKey.INJURY_HISTORY, "부상 경험", true, "무릎 통증"),
                item(AiCheckSignalKey.EXERCISE_EXPERIENCE, "운동 경험", false, "아직 확인되지 않음")
        );
        when(consultationSignalRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ConsultationSignal> result = consultationSignalService.saveSnapshot(consultation, snapshot);

        assertThat(result).hasSize(2);

        ArgumentCaptor<List<ConsultationSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(consultationSignalRepository).saveAll(captor.capture());
        List<ConsultationSignal> savedSignals = captor.getValue();
        assertThat(savedSignals)
                .extracting(ConsultationSignal::getConsultation)
                .containsExactly(consultation, consultation);
        assertThat(savedSignals)
                .extracting(ConsultationSignal::getSignalKey)
                .containsExactly(AiCheckSignalKey.INJURY_HISTORY, AiCheckSignalKey.EXERCISE_EXPERIENCE);
        assertThat(savedSignals)
                .extracting(ConsultationSignal::getDisplayOrder)
                .containsExactly(4, 3);
        assertThat(savedSignals.get(0).getValue()).isEqualTo("무릎 통증");
        assertThat(savedSignals.get(1).getConfirmed()).isFalse();
    }

    @Test
    void copyFromInquiryReturnsEmptyWhenInquiryHasNoSignals() {
        Inquiry inquiry = Inquiry.builder()
                .id(UUID.randomUUID())
                .build();
        Consultation consultation = Consultation.builder()
                .id(UUID.randomUUID())
                .build();
        when(inquirySignalRepository.findByInquiryIdOrderByDisplayOrderAsc(inquiry.getId()))
                .thenReturn(List.of());

        List<ConsultationSignal> result = consultationSignalService.copyFromInquiry(inquiry, consultation);

        assertThat(result).isEmpty();
        verify(consultationSignalRepository, never()).saveAll(anyList());
    }

    @Test
    void copyFromInquiryCopiesInquirySignalsToConsultationSignals() {
        Inquiry inquiry = Inquiry.builder()
                .id(UUID.randomUUID())
                .build();
        Consultation consultation = Consultation.builder()
                .id(UUID.randomUUID())
                .build();
        InquirySignal interestService = inquirySignal(
                inquiry,
                AiCheckSignalKey.INTEREST_SERVICE,
                "관심 상품",
                true,
                "PT"
        );
        InquirySignal exerciseGoal = inquirySignal(
                inquiry,
                AiCheckSignalKey.EXERCISE_GOAL,
                "운동 목적",
                false,
                "아직 확인되지 않음"
        );
        when(inquirySignalRepository.findByInquiryIdOrderByDisplayOrderAsc(inquiry.getId()))
                .thenReturn(List.of(interestService, exerciseGoal));
        when(consultationSignalRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ConsultationSignal> result = consultationSignalService.copyFromInquiry(inquiry, consultation);

        assertThat(result).hasSize(2);

        ArgumentCaptor<List<ConsultationSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(consultationSignalRepository).saveAll(captor.capture());
        List<ConsultationSignal> savedSignals = captor.getValue();
        assertThat(savedSignals)
                .extracting(ConsultationSignal::getConsultation)
                .containsExactly(consultation, consultation);
        assertThat(savedSignals)
                .extracting(ConsultationSignal::getSignalKey)
                .containsExactly(AiCheckSignalKey.INTEREST_SERVICE, AiCheckSignalKey.EXERCISE_GOAL);
        assertThat(savedSignals)
                .extracting(ConsultationSignal::getValue)
                .containsExactly("PT", "아직 확인되지 않음");
    }

    private AiCheckPreviewSnapshotRequest snapshot(AiCheckPreviewItemRequest... items) {
        AiCheckPreviewSnapshotRequest request = new AiCheckPreviewSnapshotRequest();
        int confirmedCount = Math.toIntExact(List.of(items).stream()
                .filter(item -> Boolean.TRUE.equals(item.getConfirmed()))
                .count());
        ReflectionTestUtils.setField(request, "confirmedCount", confirmedCount);
        ReflectionTestUtils.setField(request, "totalCount", items.length);
        ReflectionTestUtils.setField(request, "items", List.of(items));
        return request;
    }

    private AiCheckPreviewItemRequest item(
            AiCheckSignalKey key,
            String label,
            boolean confirmed,
            String value
    ) {
        AiCheckPreviewItemRequest request = new AiCheckPreviewItemRequest();
        ReflectionTestUtils.setField(request, "key", key);
        ReflectionTestUtils.setField(request, "label", label);
        ReflectionTestUtils.setField(request, "confirmed", confirmed);
        ReflectionTestUtils.setField(request, "value", value);
        return request;
    }

    private InquirySignal inquirySignal(
            Inquiry inquiry,
            AiCheckSignalKey key,
            String label,
            boolean confirmed,
            String value
    ) {
        return InquirySignal.builder()
                .inquiry(inquiry)
                .signalKey(key)
                .label(label)
                .confirmed(confirmed)
                .value(value)
                .displayOrder(key.getDisplayOrder())
                .build();
    }
}
