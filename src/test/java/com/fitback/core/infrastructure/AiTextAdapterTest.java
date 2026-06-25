package com.fitback.core.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class AiTextAdapterTest {

    @Test
    void analyzeCallsGroqChatCompletionsAndParsesJsonContent() throws Exception {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                    {"choices":[{"message":{"content":"{\\"summary\\":\\"ok\\",\\"temperature\\":\\"HOT\\",\\"leadTemperature\\":\\"HOT\\",\\"reasons\\":[],\\"signals\\":[]}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            AiTextAdapter adapter = new AiTextAdapter("key", "http://127.0.0.1:" + port,
                    "/chat/completions", "llama-3.3-70b-versatile", false, 1, 2);

            Map<String, Object> result = adapter.analyze("바로 등록하고 싶어요");

            assertThat(result).containsEntry("summary", "ok").containsEntry("temperature", "HOT");
            assertThat(requestedPath).hasValue("/chat/completions");
            assertThat(authHeader).hasValue("Bearer key");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateMessagesUnwrapsMessagesArrayFromJsonObject() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = """
                    {"choices":[{"message":{"content":"{\\"messages\\":[{\\"versionType\\":\\"SHORT\\",\\"tonePreset\\":\\"FRIENDLY\\",\\"content\\":\\"hi\\"}]}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            AiTextAdapter adapter = new AiTextAdapter("key", "http://127.0.0.1:" + port,
                    "/chat/completions", "llama-3.3-70b-versatile", false, 1, 2);

            List<Map<String, Object>> result = adapter.generateMessages(Map.of("customerName", "홍길동"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("versionType", "SHORT");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void analyzeFallsBackToLocalHeuristicWhenNotConfigured() {
        AiTextAdapter adapter = new AiTextAdapter("", "", true);

        Map<String, Object> result = adapter.analyze("바로 등록할게요");

        assertThat(result).containsEntry("provider", "LOCAL_FALLBACK");
    }
}
