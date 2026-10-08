package com.hmdp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"customer-service.mock=false", "spring.ai.mcp.client.enabled=false"})
class ChatIntegrationTest {
    private static final AtomicReference<String> body = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final HttpServer upstream = createUpstream();

    private static HttpServer createUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                        {"id":"test-chat","object":"chat.completion","created":1,
                         "model":"gpt-5.5","choices":[{"index":0,
                         "message":{"role":"assistant","content":"你好，我是智能客服。"},
                         "finish_reason":"stop"}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) {
        registry.add("customer-service.ai.base-url",
                () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("customer-service.ai.api-key", () -> "local-test-key");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    @AfterAll
    static void shutdown() {
        upstream.stop(0);
    }

    @Test
    void forwardsModelAndMessageToCompatibleEndpoint() throws Exception {
        var response = rest.postForEntity("/ai/chat",
                new ChatController.ChatRequest("你好", null), ChatController.ChatResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().reply()).isEqualTo("你好，我是智能客服。");
        assertThat(authorization.get()).isEqualTo("Bearer local-test-key");
        JsonNode sent = mapper.readTree(body.get());
        assertThat(sent.path("model").asText()).isEqualTo("gpt-5.5");
        assertThat(sent.hasNonNull("temperature")).isFalse();
        assertThat(sent.path("messages").get(1).path("content").asText()).isEqualTo("你好");
    }

    @Test
    void rejectsBlankMessage() {
        var response = rest.postForEntity("/ai/chat",
                new ChatController.ChatRequest(" ", null), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
