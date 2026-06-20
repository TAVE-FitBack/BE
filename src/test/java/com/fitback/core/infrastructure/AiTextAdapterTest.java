package com.fitback.core.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class AiTextAdapterTest {

    @Test
    void configuredAnalyzePathUsesFastApiRouteFromProperties() throws Exception {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/v1/consultations/analyze", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] body = """
                    {"summary":"ok","temperature":"HOT","leadTemperature":"HOT","reasons":[],"signals":[]}
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
                    "ai/v1/consultations/analyze", "/messages/generate", false, 1, 2);

            Map<String, Object> result = adapter.analyze("바로 등록");

            assertThat(result).containsEntry("summary", "ok");
            assertThat(requestedPath).hasValue("/ai/v1/consultations/analyze");
        } finally {
            server.stop(0);
        }
    }
}
