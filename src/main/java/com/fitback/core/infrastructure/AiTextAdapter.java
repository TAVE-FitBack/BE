package com.fitback.core.infrastructure;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiTextAdapter {

    private final String apiKey;

    public AiTextAdapter(@Value("${ai.api-key:${API_KEY_CODE:}}") String apiKey) {
        this.apiKey = apiKey;
    }

    public Map<String, Object> analyze(String rawText) {
        return Map.of(
                "temperature", rawText != null && rawText.contains("좋") ? "HOT" : "WARM",
                "temperatureBasis", "상담 원문에서 관심도와 다음 행동을 분석했습니다.",
                "positiveSignal", List.of("상담 참여", "서비스 관심"),
                "persuasionPoints", List.of("목표 기반 제안", "후속 일정 합의"),
                "provider", apiKey.isBlank() ? "LOCAL_FALLBACK" : "CONFIGURED_LLM");
    }

    public List<Map<String, Object>> generateMessages(String customerName) {
        String name = customerName == null ? "고객" : customerName;
        return List.of(
                Map.of("versionType", "SHORT", "content", name + "님, 상담 내용 확인차 연락드렸어요."),
                Map.of("versionType", "STANDARD", "content", name + "님께 맞는 혜택과 일정을 안내드립니다."),
                Map.of("versionType", "DETAILED", "content", name + "님, 편하신 시간에 답장 부탁드립니다."));
    }
}
