package com.fitback.core.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fitback.core.application.port.AiAnalysisPort;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class AiTextAdapter implements AiAnalysisPort {

    private final String apiKey;
    private final String baseUrl;
    private final boolean fallbackEnabled;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public AiTextAdapter(@Value("${ai.api-key:${API_KEY_CODE:}}") String apiKey,
            @Value("${ai.base-url:${AI_BASE_URL:}}") String baseUrl,
            @Value("${ai.fallback-enabled:false}") boolean fallbackEnabled) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.fallbackEnabled = fallbackEnabled;
    }

    public Map<String, Object> analyze(String rawText) {
        if (configured()) {
            return post("/analyze", Map.of("rawText", rawText == null ? "" : rawText),
                    new TypeReference<Map<String, Object>>() {});
        }
        requireFallback();
        return Map.of(
                "temperature", rawText != null && rawText.contains("좋") ? "HOT" : "WARM",
                "temperatureBasis", "상담 원문에서 관심도와 다음 행동을 분석했습니다.",
                "positiveSignal", List.of("상담 참여", "서비스 관심"),
                "persuasionPoints", List.of("목표 기반 제안", "후속 일정 합의"),
                "provider", apiKey.isBlank() || baseUrl.isBlank() ? "LOCAL_FALLBACK" : "FASTAPI");
    }

    public List<Map<String, Object>> generateMessages(String customerName) {
        if (configured()) {
            return post("/messages/generate", Map.of("customerName", customerName == null ? "" : customerName),
                    new TypeReference<List<Map<String, Object>>>() {});
        }
        requireFallback();
        String name = customerName == null ? "고객" : customerName;
        return List.of(
                Map.of("versionType", "SHORT", "content", name + "님, 상담 내용 확인차 연락드렸어요."),
                Map.of("versionType", "STANDARD", "content", name + "님께 맞는 혜택과 일정을 안내드립니다."),
                Map.of("versionType", "DETAILED", "content", name + "님, 편하신 시간에 답장 부탁드립니다."));
    }

    private boolean configured() {
        return !apiKey.isBlank() && !baseUrl.isBlank();
    }

    private void requireFallback() {
        if (!fallbackEnabled) {
            throw new AiProviderException("AI provider is not configured");
        }
    }

    private <T> T post(String path, Map<String, Object> body, TypeReference<T> type) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException("AI provider returned " + response.statusCode());
            }
            return mapper.readValue(response.body(), type);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProviderException("AI provider request failed", exception);
        }
    }

    public static class AiProviderException extends RuntimeException {
        public AiProviderException(String message) { super(message); }
        public AiProviderException(String message, Throwable cause) { super(message, cause); }
    }
}
