package com.hmdp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ToolRegistry {
    private final List<ToolCallback> callbacks;
    private final ObjectMapper mapper;
    public record Trace(String tool, JsonNode arguments, JsonNode result) {}

    public ToolRegistry(ObjectProvider<ToolCallbackProvider> providers, ObjectMapper mapper) {
        this.mapper = mapper;
        Map<String, ToolCallback> unique = new LinkedHashMap<>();
        providers.orderedStream().forEach(provider -> {
            for (ToolCallback callback : provider.getToolCallbacks()) {
                String name = callback.getToolDefinition().name();
                unique.putIfAbsent(name, protect(callback));
            }
        });
        callbacks = List.copyOf(unique.values());
    }

    public List<ToolCallback> callbacks() { return callbacks; }
    public List<String> names() { return callbacks.stream().map(c -> c.getToolDefinition().name()).toList(); }

    public JsonNode invoke(String name, Map<String, Object> arguments, String token, List<Trace> trace) {
        var callback = callbacks.stream().filter(c -> c.getToolDefinition().name().endsWith(name))
                .findFirst().orElseThrow(() -> new IllegalStateException("MCP工具未连接"));
        try {
            return mapper.readTree(callback.call(mapper.writeValueAsString(arguments),
                    new ToolContext(Map.of("accessToken", token, "trace", trace))));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("工具数据解析失败", e);
        }
    }

    private ToolCallback protect(ToolCallback delegate) {
        var original = delegate.getToolDefinition();
        ObjectNode schema;
        try {
            schema = (ObjectNode) mapper.readTree(original.inputSchema());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        boolean inject = schema.path("properties").has("accessToken");
        if (inject) {
            ((ObjectNode) schema.get("properties")).remove("accessToken");
            if (schema.get("required") instanceof ArrayNode required) {
                for (int i = required.size() - 1; i >= 0; i--) {
                    if ("accessToken".equals(required.get(i).asText())) required.remove(i);
                }
            }
        }
        var definition = ToolDefinition.builder().name(original.name())
                .description(original.description()).inputSchema(schema.toString()).build();
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() { return definition; }
            public String call(String input) { return call(input, new ToolContext(Map.of())); }
            @SuppressWarnings("unchecked")
            public String call(String input, ToolContext context) {
                try {
                    if (context.getContext().get("trace") instanceof List<?> existing && existing.size() >= 8) {
                        throw new IllegalStateException("单轮工具调用已达上限");
                    }
                    ObjectNode arguments = (ObjectNode) mapper.readTree(input);
                    arguments.remove("accessToken");
                    JsonNode visible = arguments.deepCopy();
                    if (inject) arguments.put("accessToken", String.valueOf(context.getContext().getOrDefault("accessToken", "")));
                    String raw = delegate.call(arguments.toString());
                    // MCP 回调通常返回一个文本内容数组，取出其中的业务JSON。
                    JsonNode result = mapper.readTree(raw);
                    if (result.isArray() && !result.isEmpty() && result.get(0).has("text")) {
                        result = mapper.readTree(result.get(0).get("text").asText());
                    }
                    if (context.getContext().get("trace") instanceof List<?> traces) {
                        ((List<Trace>) traces).add(new Trace(definition.name(), visible, result));
                    }
                    return result.toString();
                } catch (java.io.IOException e) { throw new IllegalStateException("MCP返回数据格式错误", e); }
            }
        };
    }
}
