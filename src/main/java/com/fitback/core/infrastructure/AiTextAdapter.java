package com.fitback.core.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final int maxAttempts;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public AiTextAdapter(@Value("${ai.api-key:${API_KEY_CODE:}}") String apiKey,
            @Value("${ai.base-url:${AI_BASE_URL:}}") String baseUrl,
            @Value("${ai.fallback-enabled:false}") boolean fallbackEnabled) {
        this(apiKey, baseUrl, fallbackEnabled, 2, 10);
    }

    public AiTextAdapter(String apiKey, String baseUrl, boolean fallbackEnabled, int maxAttempts, long timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.fallbackEnabled = fallbackEnabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public Map<String, Object> analyze(String rawText) {
        if (configured()) {
            return post("/analyze", Map.of("rawText", rawText == null ? "" : rawText),
                    new TypeReference<Map<String, Object>>() {});
        }
        requireFallback();
        return fallbackAnalysis(rawText);
    }

    public List<Map<String, Object>> generateMessages(String customerName) {
        if (configured()) {
            return post("/messages/generate", Map.of("customerName", customerName == null ? "" : customerName),
                    new TypeReference<List<Map<String, Object>>>() {});
        }
        requireFallback();
        String name = customerName == null || customerName.isBlank() ? "고객" : customerName;
        return List.of(
                Map.of("versionType", "SHORT", "tonePreset", "FRIENDLY",
                        "content", name + "님, 상담 내용 확인차 연락드립니다. 편하신 시간에 방문 일정을 도와드릴게요."),
                Map.of("versionType", "STANDARD", "tonePreset", "PROFESSIONAL",
                        "content", name + "님께 맞는 이용 옵션과 다음 방문 일정을 안내드리겠습니다."),
                Map.of("versionType", "DETAILED", "tonePreset", "CARING",
                        "content", name + "님이 말씀해주신 목적과 우려 사항을 기준으로 가장 부담 없는 선택지를 정리해드리겠습니다."));
    }

    private boolean configured() {
        return !apiKey.isBlank() && !baseUrl.isBlank();
    }

    private void requireFallback() {
        if (!fallbackEnabled) {
            throw new AiProviderException("AI provider is not configured");
        }
    }

    private Map<String, Object> fallbackAnalysis(String rawText) {
        String text = rawText == null ? "" : rawText;
        boolean hot = containsAny(text, "바로", "등록", "결제", "좋", "예약");
        boolean cold = containsAny(text, "비싸", "고민", "나중", "비교", "망설");

        List<Map<String, Object>> reasons = new ArrayList<>();
        reasons.add(Map.of(
                "reasonRole", "PRIMARY",
                "reasonType", cold ? "PRICE_CONCERN" : "NEEDS_CONFIRMATION",
                "reasonBasis", cold ? "가격 또는 비교 검토 표현이 포함되어 있습니다." : "방문 목적은 있으나 최종 결정 정보가 부족합니다.",
                "confidence", cold ? "MEDIUM" : "LOW"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("temperature", hot ? "HOT" : "WARM");
        result.put("leadTemperature", hot ? "HOT" : "WARM");
        result.put("temperatureBasis", hot ? "즉시 등록 또는 예약 가능성이 있는 표현이 확인되었습니다."
                : "관심은 있으나 다음 안내가 필요한 상담으로 분류했습니다.");
        result.put("summary", summarize(text));
        result.put("positiveSignal", List.of("상담 참여", "서비스 관심"));
        result.put("signals", List.of(
                Map.of("signalType", "INTEREST", "signalValue", hot ? "HIGH" : "MEDIUM",
                        "confidence", "MEDIUM", "evidenceText", summarize(text)),
                Map.of("signalType", "NEXT_ACTION", "signalValue", "FOLLOW_UP",
                        "confidence", "MEDIUM", "evidenceText", "방문/연락 후속 안내 필요")));
        result.put("missingQuestions", List.of("운동 경험이 없는 분인가요?", "방문 경로는 무엇인가요?"));
        result.put("persuasionPoints", List.of("이용 가능 시간대 안내", "관심 목적에 맞춘 혜택 제안"));
        result.put("nextBestAction", "상담 요약을 확인한 뒤 방문 예상 시간과 우려 사항을 보완해 안내하세요.");
        result.put("recommendContactDate", LocalDate.now().plusDays(1).toString());
        result.put("reasons", reasons);
        result.put("provider", "LOCAL_FALLBACK");
        return result;
    }

    private String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "상담 메모가 비어 있어 추가 정보 입력이 필요합니다.";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 120 ? compact.substring(0, 120) + "..." : compact;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private <T> T post(String path, Map<String, Object> body, TypeReference<T> type) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(requestTimeout)
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
                lastFailure = exception;
            } catch (Exception exception) {
                lastFailure = new AiProviderException("AI provider request failed", exception);
            }
        }
        throw lastFailure == null ? new AiProviderException("AI provider request failed") : lastFailure;
    }

    public static class AiProviderException extends RuntimeException {
        public AiProviderException(String message) {
            super(message);
        }

        public AiProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
