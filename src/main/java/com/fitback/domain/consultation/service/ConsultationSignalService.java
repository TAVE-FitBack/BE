package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.entity.ConsultationSignal;
import com.fitback.domain.consultation.repository.ConsultationSignalRepository;
import com.fitback.domain.consultation.support.AiCheckPreviewSnapshotValidator;
import com.fitback.domain.inquiry.entity.Inquiry;
import com.fitback.domain.inquiry.entity.InquirySignal;
import com.fitback.domain.inquiry.repository.InquirySignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationSignalService {

    private final ConsultationSignalRepository consultationSignalRepository;
    private final InquirySignalRepository inquirySignalRepository;

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
