package com.fitback.domain.consultation.event;

import com.fitback.domain.consultation.service.ConsultationAiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ConsultationAiAnalysisEventListener {

    private final ConsultationAiAnalysisService consultationAiAnalysisService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConsultationCreated(ConsultationCreatedEvent event) {
        consultationAiAnalysisService.analyzeConsultation(event.consultationId());
    }
}
