package com.hmdp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@RestController
@RequestMapping("/ai")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ObjectProvider<ChatClient> client;
    private final ConversationStore store;
    private final MockConversation mockConversation;
    private final ToolRegistry tools;
    private final boolean mock;
    private final RestClient business;

    public ChatController(ObjectProvider<ChatClient> client, ConversationStore store,
            MockConversation mockConversation, ToolRegistry tools,
            @Value("${customer-service.mock}") boolean mock,
            @Value("${customer-service.business-url}") String businessUrl) {
        this.client = client;
        this.store = store;
        this.mockConversation = mockConversation;
        this.tools = tools;
        this.mock = mock;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        business = RestClient.builder().baseUrl(businessUrl).requestFactory(factory).build();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "mode", mock ? "mock" : "live", "tools", tools.names());
    }

    private String owner(HttpSession session, String token) {
        if (token.isBlank()) return session.getId() + ":guest";
        JsonNode result;
        try {
            result = business.get().uri("/user/me").header("authorization", token)
                    .retrieve().body(JsonNode.class);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录校验失败，请重新登录或检查业务后端");
        }
        if (result == null || !result.path("success").asBoolean() || !result.path("data").has("id")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return session.getId() + ":user:" + result.path("data").path("id").asText();
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "authorization", defaultValue = "") String token,
            HttpSession session) {
        ConversationStore.Conversation c = store.get(owner(session, token), request.conversationId());
        List<ToolRegistry.Trace> traces = new ArrayList<>();
        synchronized (c) {
            long started = System.nanoTime();
            log.info("收到对话请求，模式={}", mock ? "mock" : "live");
            try {
                String reply;
                if (mock) {
                    reply = mockConversation.reply(request.message(), c, token, traces);
                } else {
                    reply = client.getObject().prompt().user(request.message())
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, c.id))
                            .toolContext(Map.of("accessToken", token, "trace", traces))
                            .call().content();
                }
                if (reply == null || reply.isBlank()) throw new IllegalStateException("模型返回空内容");
                return ResponseEntity.ok(new ChatResponse(true, reply, null, c.id, mock ? "mock" : "live", traces));
            } catch (RuntimeException ex) {
                Throwable cause = ex;
                while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                log.warn("对话失败，异常类型={}，根因类型={}", ex.getClass().getSimpleName(), cause.getClass().getSimpleName());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ChatResponse(false, null,
                        mock ? "业务工具调用失败，请确认MCP服务和业务后端已启动。" : "模型或业务工具调用失败，请检查服务、网络及模型配置。",
                        c.id, mock ? "mock" : "live", traces));
            } finally {
                c.touched = System.currentTimeMillis();
                log.info("对话请求结束，耗时={}ms，工具调用次数={}", (System.nanoTime() - started) / 1_000_000, traces.size());
            }
        }
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, Boolean> delete(@PathVariable String id,
            @RequestHeader(value = "authorization", defaultValue = "") String token, HttpSession session) {
        store.delete(owner(session, token), id);
        return Map.of("success", true);
    }

    public record ChatRequest(@NotBlank @Size(max = 4000) String message,
                              @Size(max = 64) String conversationId) {}
    public record ChatResponse(boolean success, String reply, String errorMsg,
                               String conversationId, String mode, List<ToolRegistry.Trace> tools) {}
}
