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

    private static final String ANALYZE_SYSTEM_PROMPT = """
            너는 피트니스/필라테스 매장의 상담 메모를 분석하는 CRM 어시스턴트다.
            상담 원문을 읽고 아래 키를 모두 포함한 JSON 객체만 반환하라. 다른 설명, 코드블록 표시는 출력하지 마라.

            {
              "temperature": "HOT|WARM|COLD",
              "leadTemperature": "temperature와 동일한 값",
              "temperatureBasis": "온도 판단 근거 한두 문장",
              "summary": "상담 내용 한두 문장 요약",
              "positiveSignal": ["긍정 신호 키워드", "..."],
              "signals": [{"signalType": "INTEREST|NEXT_ACTION|OBJECTION", "signalValue": "HIGH|MEDIUM|LOW 또는 행동명", "confidence": "LOW|MEDIUM|HIGH", "evidenceText": "근거 문장"}],
              "missingQuestions": ["추가로 확인이 필요한 질문", "..."],
              "persuasionPoints": ["설득 포인트", "..."],
              "nextBestAction": "다음 행동 제안 한 문장",
              "recommendContactDate": "YYYY-MM-DD",
              "reasons": [{"reasonRole": "PRIMARY", "reasonType": "PRICE_CONCERN|NEEDS_CONFIRMATION|SCHEDULE_CONFLICT|TRUST_CONCERN|OTHER", "reasonBasis": "근거", "confidence": "LOW|MEDIUM|HIGH"}]
            }

            reasons 배열은 reasonRole이 PRIMARY인 항목을 정확히 1개 포함해야 하고, 필요하면 SUB1/SUB2를 추가로 포함할 수 있다.
            상담 원문의 구체적인 내용을 반영해서 매번 다르게 분석하라. 입력이 비어 있으면 정보 부족으로 분석하라.
            """;

    private static final String MESSAGE_SYSTEM_PROMPT = """
            너는 피트니스/필라테스 매장에서 회원권 상담을 진행한 고객에게 보낼 후속 메시지를 작성하는 카피라이터다.
            이 고객은 상담만 받았거나 등록을 고민 중인 상태이며, 제품을 구매하거나 이미 등록을 완료한 고객이 아니다.
            "구매하신 제품", "주문", "배송", "A/S" 같은 쇼핑/제품 관련 표현은 절대 사용하지 말고, 운동 상담·회원권 등록·방문 일정 안내에 맞는 표현만 사용하라.
            사용자 메시지로 주어지는 상담 맥락(온도, 상담 요약, 다음 행동, 미등록 이유, 설득 포인트)을 반드시 반영해서 그 고객에게만 맞는 메시지를 작성하라.

            아래 키를 모두 포함한 JSON 객체만 반환하라. 다른 설명, 코드블록 표시는 출력하지 마라.

            {"messages": [{"versionType": "SHORT|STANDARD|DETAILED", "tonePreset": "FRIENDLY|PROFESSIONAL|CARING", "content": "고객 이름을 포함한 메시지 본문"}]}

            messages 배열은 정확히 3개 항목을 포함하고, versionType은 SHORT/STANDARD/DETAILED를 각각 정확히 한 번씩 사용해야 한다.
            """;

    private final String apiKey;
    private final String baseUrl;
    private final String chatPath;
    private final String model;
    private final boolean fallbackEnabled;
    private final int maxAttempts;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public AiTextAdapter(@Value("${ai.api-key:${API_KEY_CODE:}}") String apiKey,
            @Value("${ai.base-url:${AI_BASE_URL:https://api.groq.com/openai/v1}}") String baseUrl,
            @Value("${ai.chat-path:/chat/completions}") String chatPath,
            @Value("${ai.model:llama-3.3-70b-versatile}") String model,
            @Value("${ai.fallback-enabled:true}") boolean fallbackEnabled,
            @Value("${ai.max-attempts:2}") int maxAttempts,
            @Value("${ai.timeout-seconds:10}") long timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.chatPath = normalizePath(chatPath);
        this.model = model == null || model.isBlank() ? "llama-3.3-70b-versatile" : model;
        this.fallbackEnabled = fallbackEnabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public AiTextAdapter(String apiKey, String baseUrl, boolean fallbackEnabled, int maxAttempts, long timeoutSeconds) {
        this(apiKey, baseUrl, "/chat/completions", "llama-3.3-70b-versatile", fallbackEnabled, maxAttempts, timeoutSeconds);
    }

    public AiTextAdapter(String apiKey, String baseUrl, boolean fallbackEnabled) {
        this(apiKey, baseUrl, fallbackEnabled, 2, 10);
    }

    public Map<String, Object> analyze(String rawText) {
        String text = rawText == null ? "" : rawText;
        if (configured()) {
            String content = chat(ANALYZE_SYSTEM_PROMPT, text.isBlank() ? "상담 메모가 비어 있습니다." : text);
            return parseJsonObject(content);
        }
        requireFallback();
        return fallbackAnalysis(text);
    }

    public List<Map<String, Object>> generateMessages(Map<String, Object> context) {
        String name = stringOrDefault(context.get("customerName"), "고객");
        if (configured()) {
            String content = chat(MESSAGE_SYSTEM_PROMPT, buildMessageContext(context, name));
            return parseMessages(content);
        }
        requireFallback();
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

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
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

    private String buildMessageContext(Map<String, Object> context, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("매장 업종: 피트니스/필라테스 회원권 상담\n");
        sb.append("고객 이름: ").append(name).append('\n');
        appendIfPresent(sb, "현재 관심도(온도)", context.get("leadTemperature"));
        appendIfPresent(sb, "온도 판단 근거", context.get("temperatureBasis"));
        appendIfPresent(sb, "상담 요약", context.get("consultationSummary"));
        appendIfPresent(sb, "다음 행동 제안", context.get("nextBestAction"));
        appendIfPresent(sb, "미등록/보류 이유", context.get("primaryReason"));
        appendIfPresent(sb, "설득 포인트", context.get("persuasionPoints"));
        sb.append("\n위 상담 맥락을 반영해서 이 고객에게 보낼 후속 메시지 3종을 작성해줘.");
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = value instanceof List<?> list ? String.join(", ", list.stream().map(String::valueOf).toList())
                : String.valueOf(value);
        if (text.isBlank() || "null".equals(text)) {
            return;
        }
        sb.append(label).append(": ").append(text).append('\n');
    }

    private String stringOrDefault(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String chat(String systemPrompt, String userContent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.4);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)));

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + chatPath))
                        .timeout(requestTimeout)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new AiProviderException("AI provider returned " + response.statusCode() + ": " + response.body());
                }
                return extractContent(response.body());
            } catch (AiProviderException exception) {
                lastFailure = exception;
            } catch (Exception exception) {
                lastFailure = new AiProviderException("AI provider request failed", exception);
            }
        }
        throw lastFailure == null ? new AiProviderException("AI provider request failed") : lastFailure;
    }

    private String extractContent(String responseBody) {
        Map<String, Object> parsed = mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
        Object choicesValue = parsed.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new AiProviderException("AI provider returned no choices");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            throw new AiProviderException("AI provider returned malformed choice");
        }
        Object messageValue = choice.get("message");
        Object content = messageValue instanceof Map<?, ?> message ? message.get("content") : null;
        if (content == null || String.valueOf(content).isBlank()) {
            throw new AiProviderException("AI provider returned empty content");
        }
        return String.valueOf(content);
    }

    private Map<String, Object> parseJsonObject(String content) {
        try {
            return mapper.readValue(stripCodeFence(content), new TypeReference<Map<String, Object>>() {});
        } catch (AiProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProviderException("Failed to parse AI analysis response", exception);
        }
    }

    private List<Map<String, Object>> parseMessages(String content) {
        try {
            Map<String, Object> parsed = mapper.readValue(stripCodeFence(content), new TypeReference<Map<String, Object>>() {});
            Object messages = parsed.get("messages");
            if (!(messages instanceof List<?> list)) {
                throw new AiProviderException("AI provider returned no messages array");
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(cast(map));
                }
            }
            return result;
        } catch (AiProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProviderException("Failed to parse AI message response", exception);
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n", "").replaceFirst("```\\s*$", "").trim();
        }
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> source) {
        return (Map<String, Object>) source;
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
