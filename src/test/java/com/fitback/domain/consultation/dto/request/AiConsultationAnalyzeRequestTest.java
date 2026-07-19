package com.fitback.domain.consultation.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AiConsultationAnalyzeRequestTest {

    @Test
    @DisplayName("1차 구현에서는 AI 본분석 요청에 중간분석 signal을 포함하지 않는다")
    void analyzeRequestDoesNotIncludeAiCheckPreviewSignals() {
        assertThat(Arrays.stream(AiConsultationAnalyzeRequest.class.getDeclaredFields())
                .map(Field::getName))
                .containsExactly(
                        "customer",
                        "consultation",
                        "service",
                        "storeContext",
                        "attachedMaterials"
                )
                .doesNotContain(
                        "signal",
                        "signals",
                        "aiCheckPreview",
                        "consultationSignal",
                        "consultationSignals",
                        "consultationSignalSnapshot"
                );
    }
}
