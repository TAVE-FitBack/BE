package com.fitback.domain.inquiry.service;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewItemRequest;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import com.fitback.domain.inquiry.entity.Inquiry;
import com.fitback.domain.inquiry.entity.InquirySignal;
import com.fitback.domain.inquiry.repository.InquirySignalRepository;
import com.fitback.global.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquirySignalServiceTest {

    @Mock
    private InquirySignalRepository inquirySignalRepository;

    @InjectMocks
    private InquirySignalService inquirySignalService;

    @Test
    void saveSnapshotReturnsEmptyWhenSnapshotIsNull() {
        Inquiry inquiry = Inquiry.builder()
                .id(UUID.randomUUID())
                .build();

        List<InquirySignal> result = inquirySignalService.saveSnapshot(inquiry, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(inquirySignalRepository);
    }

    @Test
    void saveSnapshotMapsPreviewItemsToInquirySignals() {
        Inquiry inquiry = Inquiry.builder()
                .id(UUID.randomUUID())
                .build();
        AiCheckPreviewSnapshotRequest snapshot = snapshot(
                item(AiCheckSignalKey.INTEREST_SERVICE, "관심 상품", true, "PT"),
                item(AiCheckSignalKey.EXERCISE_GOAL, "운동 목적", false, "아직 확인되지 않음")
        );
        when(inquirySignalRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<InquirySignal> result = inquirySignalService.saveSnapshot(inquiry, snapshot);

        assertThat(result).hasSize(2);

        ArgumentCaptor<List<InquirySignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(inquirySignalRepository).saveAll(captor.capture());
        List<InquirySignal> savedSignals = captor.getValue();
        assertThat(savedSignals)
                .extracting(InquirySignal::getInquiry)
                .containsExactly(inquiry, inquiry);
        assertThat(savedSignals)
                .extracting(InquirySignal::getSignalKey)
                .containsExactly(AiCheckSignalKey.INTEREST_SERVICE, AiCheckSignalKey.EXERCISE_GOAL);
        assertThat(savedSignals)
                .extracting(InquirySignal::getDisplayOrder)
                .containsExactly(1, 2);
        assertThat(savedSignals.get(0).getValue()).isEqualTo("PT");
        assertThat(savedSignals.get(1).getConfirmed()).isFalse();
    }

    @Test
    void saveSnapshotRejectsDuplicateKeys() {
        Inquiry inquiry = Inquiry.builder()
                .id(UUID.randomUUID())
                .build();
        AiCheckPreviewSnapshotRequest snapshot = snapshot(
                item(AiCheckSignalKey.INTEREST_SERVICE, "관심 상품", true, "PT"),
                item(AiCheckSignalKey.INTEREST_SERVICE, "관심 상품", false, "아직 확인되지 않음")
        );

        assertThatThrownBy(() -> inquirySignalService.saveSnapshot(inquiry, snapshot))
                .isInstanceOf(BusinessException.class);
        verify(inquirySignalRepository, never()).saveAll(anyList());
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
}
