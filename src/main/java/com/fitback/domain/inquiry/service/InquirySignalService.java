package com.fitback.domain.inquiry.service;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
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
public class InquirySignalService {

    private final InquirySignalRepository inquirySignalRepository;

    @Transactional
    public List<InquirySignal> saveSnapshot(Inquiry inquiry, AiCheckPreviewSnapshotRequest snapshot) {
        if (snapshot == null) {
            return List.of();
        }

        AiCheckPreviewSnapshotValidator.validate(snapshot);

        List<InquirySignal> signals = snapshot.getItems().stream()
                .map(item -> InquirySignal.from(inquiry, item))
                .toList();

        return inquirySignalRepository.saveAll(signals);
    }
}
