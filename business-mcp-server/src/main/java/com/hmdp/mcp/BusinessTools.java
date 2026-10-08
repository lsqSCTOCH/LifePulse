package com.hmdp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.util.Map;

@Component
public class BusinessTools {
    private final RestClient rest;
    private final ObjectMapper mapper;

    public BusinessTools(@Value("${business.base-url}") String baseUrl, ObjectMapper mapper) {
        this.mapper = mapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    ToolCallbackProvider businessToolProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(this).build();
    }

    @Tool(description = "按商户名称关键词查询真实商户，返回第一页最多10项，包含商户ID，供详情和优惠券查询使用。")
    public JsonNode searchShops(@ToolParam(description = "商户名称关键词，空字符串表示列出商户") String keyword) {
        if (keyword == null || keyword.length() > 100) return failure("关键词不能超过100字");
        return get("/shop/of/name?current=1&name={name}", "", keyword);
    }

    @Tool(description = "按商户ID查询地址、均价、评分、营业时间等真实详情；均价单位元，评分是十分制需除以10。")
    public JsonNode getShopDetail(@ToolParam(description = "商户ID") String shopId) {
        return numeric(shopId) ? get("/shop/{id}", "", shopId) : failure("商户ID必须为正整数");
    }

    @Tool(description = "查询指定商户的优惠券，payValue和actualValue单位为分，展示时除以100；不要把数据库记录解释为一定可抢购。")
    public JsonNode listShopVouchers(@ToolParam(description = "商户ID") String shopId) {
        return numeric(shopId) ? get("/voucher/list/{id}", "", shopId) : failure("商户ID必须为正整数");
    }

    @Tool(description = "查询当前登录用户的最近10笔优惠券订单及真实状态。")
    public JsonNode getMyOrders(@ToolParam(description = "由可信客服服务注入的登录凭证") String accessToken) {
        return get("/voucher-order/mine", accessToken);
    }

    @Tool(description = "查询当前登录用户的指定订单状态，只允许查看本人订单；订单ID为字符串，不能四舍五入。")
    public JsonNode getMyOrderStatus(@ToolParam(description = "订单ID，保持原始字符串") String orderId,
                                   @ToolParam(description = "由可信客服服务注入的登录凭证") String accessToken) {
        return numeric(orderId) ? get("/voucher-order/mine/{id}", accessToken, orderId) : failure("订单ID必须为正整数");
    }

    private boolean numeric(String value) {
        return value != null && value.matches("[1-9][0-9]{0,18}");
    }

    private JsonNode get(String path, String token, Object... args) {
        if (path.startsWith("/voucher-order") && (token == null || token.isBlank())) return failure("请先登录，再查询本人订单");
        try {
            var request = rest.get().uri(path, args);
            if (token != null && !token.isBlank()) request.header("authorization", token);
            JsonNode result = request.retrieve().body(JsonNode.class);
            return result == null ? failure("业务服务返回空数据") : result;
        } catch (RestClientResponseException e) {
            return failure(e.getStatusCode().value() == 401 ? "登录已过期，请重新登录" : "业务查询失败，请稍后重试");
        } catch (RuntimeException e) {
            return failure("无法连接业务后端，请确认8081服务已启动");
        }
    }

    private JsonNode failure(String message) {
        return mapper.valueToTree(Map.of("success", false, "errorMsg", message));
    }
}
