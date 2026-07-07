package com.fitback.domain.consultation.event;

import com.fitback.domain.consultation.service.ConsultationAiAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConsultationAiAnalysisEventListenerTest {

    @Test
    @DisplayName("상담 등록 이벤트 리스너는 커밋 이후 비동기로 AI 분석을 실행한다")
    void handleConsultationCreatedRunsAfterCommitAsync() throws NoSuchMethodException {
        Method method = ConsultationAiAnalysisEventListener.class.getDeclaredMethod(
                "handleConsultationCreated",
                ConsultationCreatedEvent.class
        );

        TransactionalEventListener transactionalEventListener = method.getAnnotation(TransactionalEventListener.class);

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        assertThat(transactionalEventListener).isNotNull();
        assertThat(transactionalEventListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("상담 등록 이벤트 수신 시 AI 분석 서비스에 consultationId를 전달한다")
    void handleConsultationCreatedDelegatesToAnalysisService() {
        ConsultationAiAnalysisService analysisService = mock(ConsultationAiAnalysisService.class);
        ConsultationAiAnalysisEventListener listener = new ConsultationAiAnalysisEventListener(analysisService);
        UUID consultationId = UUID.randomUUID();

        listener.handleConsultationCreated(new ConsultationCreatedEvent(consultationId));

        verify(analysisService).analyzeConsultation(consultationId);
    }
}
