package com.hmdp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class ToolPrivacyTest {
    @Test
    void keepsCredentialOutOfModelSchemaAndTraceAndOverridesModelInput() throws Exception {
        var received = new AtomicReference<String>();
        ToolCallback original = new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("getMyOrderStatus").description("查订单")
                        .inputSchema("""
                          {"type":"object","properties":{"orderId":{"type":"string"},
                          "accessToken":{"type":"string"}},"required":["orderId","accessToken"]}
                          """).build();
            }
            public String call(String input) { received.set(input); return "{\"success\":true,\"data\":{\"status\":2}}"; }
        };
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("tools", ToolCallbackProvider.from(original));
        var mapper = new ObjectMapper();
        var registry = new ToolRegistry(factory.getBeanProvider(ToolCallbackProvider.class), mapper);
        var callback = registry.callbacks().get(0);
        assertThat(callback.getToolDefinition().inputSchema()).doesNotContain("accessToken");
        List<ToolRegistry.Trace> trace = new ArrayList<>();
        callback.call("{\"orderId\":\"123\",\"accessToken\":\"model-supplied\"}",
                new ToolContext(Map.of("accessToken", "trusted-session-token", "trace", trace)));
        assertThat(mapper.readTree(received.get()).path("accessToken").asText()).isEqualTo("trusted-session-token");
        assertThat(mapper.writeValueAsString(trace)).doesNotContain("trusted-session-token", "model-supplied", "accessToken");
    }
}
