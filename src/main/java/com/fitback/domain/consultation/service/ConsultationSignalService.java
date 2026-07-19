package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.entity.ConsultationSignal;
import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import com.fitback.domain.consultation.repository.ConsultationSignalRepository;
import com.fitback.domain.consultation.support.AiCheckPreviewSnapshotValidator;
import com.fitback.domain.inquiry.entity.Inquiry;
import com.fitback.domain.inquiry.entity.InquirySignal;
import com.fitback.domain.inquiry.repository.InquirySignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationSignalService {

    private static final List<AiCheckSignalKey> CUSTOMER_DETAIL_CARD_KEYS = List.of(
            AiCheckSignalKey.EXERCISE_GOAL,
            AiCheckSignalKey.INJURY_HISTORY,
            AiCheckSignalKey.INTEREST_SERVICE,
            AiCheckSignalKey.EXERCISE_EXPERIENCE
    );

    private final ConsultationSignalRepository consultationSignalRepository;
    private final InquirySignalRepository inquirySignalRepository;

    public List<ConsultationSignal> findCustomerDetailCardSignals(UUID consultationId) {
        if (consultationId == null) {
            return List.of();
        }

        Map<AiCheckSignalKey, ConsultationSignal> signalByKey = consultationSignalRepository
                .findByConsultationIdOrderByDisplayOrderAsc(consultationId)
                .stream()
                .filter(signal -> CUSTOMER_DETAIL_CARD_KEYS.contains(signal.getSignalKey()))
                .collect(Collectors.toMap(
                        ConsultationSignal::getSignalKey,
                        Function.identity(),
                        (first, ignored) -> first,
                        () -> new EnumMap<>(AiCheckSignalKey.class)
                ));

        return CUSTOMER_DETAIL_CARD_KEYS.stream()
                .map(signalByKey::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public List<ConsultationSignal> saveSnapshot(
            Consultation consultation,
            AiCheckPreviewSnapshotRequest snapshot
    ) {
        if (snapshot == null) {
            return List.of();
        }

        AiCheckPreviewSnapshotValidator.validate(snapshot);

        List<ConsultationSignal> signals = snapshot.getItems().stream()
                .map(item -> ConsultationSignal.from(consultation, item))
                .toList();

        return consultationSignalRepository.saveAll(signals);
    }

    @Transactional
    public List<ConsultationSignal> copyFromInquiry(Inquiry inquiry, Consultation consultation) {
        List<InquirySignal> inquirySignals = inquirySignalRepository
                .findByInquiryIdOrderByDisplayOrderAsc(inquiry.getId());
        if (inquirySignals.isEmpty()) {
            return List.of();
        }

        List<ConsultationSignal> consultationSignals = inquirySignals.stream()
                .map(inquirySignal -> ConsultationSignal.copyOf(consultation, inquirySignal))
                .toList();

        return consultationSignalRepository.saveAll(consultationSignals);
    }
}
