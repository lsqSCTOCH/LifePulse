package com.hmdp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class MockConversation {
    private final ToolRegistry tools;
    public MockConversation(ToolRegistry tools) { this.tools = tools; }

    public String reply(String message, ConversationStore.Conversation c, String token, List<ToolRegistry.Trace> traces) {
        if (message.contains("订单")) {
            var matcher = Pattern.compile("订单(?:号)?[：:#\\s]*([0-9]+)").matcher(message);
            JsonNode result = matcher.find()
                    ? tools.invoke("getMyOrderStatus", Map.of("orderId", matcher.group(1)), token, traces)
                    : tools.invoke("getMyOrders", Map.of(), token, traces);
            if (!result.path("success").asBoolean()) return error(result);
            JsonNode data = result.path("data");
            if (data.isArray()) {
                if (data.isEmpty()) return "你目前没有订单记录。";
                StringBuilder text = new StringBuilder("你最近的订单：\n");
                data.forEach(o -> text.append("订单 ").append(o.path("id").asText()).append("：").append(o.path("statusLabel").asText()).append('\n'));
                return text.toString();
            }
            return "订单 " + data.path("id").asText() + "：" + data.path("statusLabel").asText();
        }
        var explicit = Pattern.compile("(?:商户|店铺|商家)(?:ID|id|编号)?[：:#\\s]*([0-9]+)").matcher(message);
        boolean reference = message.contains("第一家") || message.contains("第二家") || message.contains("第三家") || message.contains("这家") || message.contains("它");
        boolean voucher = message.contains("优惠") || message.contains("券");
        boolean selectedById = explicit.find();
        if (selectedById) {
            JsonNode result = tools.invoke("getShopDetail", Map.of("shopId", explicit.group(1)), token, traces);
            if (!result.path("success").asBoolean() || result.path("data").isMissingNode() || result.path("data").isNull()) return error(result);
            c.selectedShop = result.path("data");
        } else if (reference && c.shops != null) {
            int index = message.contains("第二家") ? 1 : message.contains("第三家") ? 2 : message.contains("第一家") ? 0 : -1;
            if (index >= 0) {
                if (index >= c.shops.size()) return "上次结果没有这么多商户，请重新选择。";
                c.selectedShop = c.shops.get(index);
            }
        }
        if (voucher || reference || selectedById || c.selectedShop != null && message.contains("详情")) {
            if (c.selectedShop == null) return "请先查询商户，再说“第一家有什么优惠券”，或直接输入“商户1的优惠券”。";
            String shopId = c.selectedShop.path("id").asText();
            if (voucher) {
                JsonNode result = tools.invoke("listShopVouchers", Map.of("shopId", shopId), token, traces);
                if (!result.path("success").asBoolean()) return error(result);
                JsonNode data = result.path("data");
                if (!data.isArray() || data.isEmpty()) return c.selectedShop.path("name").asText() + "暂无优惠券记录。";
                StringBuilder text = new StringBuilder(c.selectedShop.path("name").asText() + "的优惠券：\n");
                data.forEach(v -> text.append(v.path("title").asText()).append("，售价 ").append(money(v.path("payValue"))).append(" 元，面值 ").append(money(v.path("actualValue"))).append(" 元\n使用规则：").append(v.path("rules").asText("未提供").replace("\\n", "\n")).append('\n'));
                return text.append("是否可购买还需核对活动时间、库存和优惠券状态。").toString();
            }
            JsonNode result = tools.invoke("getShopDetail", Map.of("shopId", shopId), token, traces);
            if (!result.path("success").asBoolean()) return error(result);
            JsonNode s = result.path("data");
            if (s.path("demo").asBoolean()) return s.path("name").asText() + "\n这是演示店铺，用于体验分类和详情，不对应真实地址、价格或营业时间。";
            return s.path("name").asText() + "\n地址：" + s.path("address").asText() + "\n均价：" + (s.path("avgPrice").asLong() > 0 ? s.path("avgPrice").asText()+"元（参考）" : "暂无公开价格，请咨询商户") + "\n营业时间：" + s.path("openHours").asText();
        }
        if (message.contains("商户") || message.contains("店") || message.contains("查询") || message.contains("搜索") || message.contains("火锅") || message.contains("美食")) {
            String keyword = message.replaceAll("^(请|帮我|帮忙|查一下|查询|查找|搜索|搜一下|找一下|找|看看|列出|推荐|有哪些)+", "")
                    .replaceAll("[？?。！!\\s]", "");
            if (Set.of("商户", "商户列表", "店铺", "美食", "有什么店", "店铺列表", "商家").contains(keyword)) keyword = "";
            if (keyword.endsWith("店") && keyword.length() > 1) keyword = keyword.substring(0, keyword.length() - 1);
            JsonNode result = tools.invoke("searchShops", Map.of("keyword", keyword), token, traces);
            if (!result.path("success").asBoolean()) return error(result);
            c.shops = result.path("data");
            c.selectedShop = null;
            if (!c.shops.isArray() || c.shops.isEmpty()) return "没有找到匹配商户，可以试试“查询商户”或更短的名称关键词。";
            StringBuilder text = new StringBuilder("查询到以下商户（第一页）：\n");
            for (int i = 0; i < c.shops.size(); i++) {
                JsonNode s = c.shops.get(i);
                text.append(i + 1).append(". ").append(s.path("name").asText()).append("（ID ").append(s.path("id").asText()).append("）\n");
            }
            return text.append("可以继续问“第一家有什么优惠券”或“第二家详情”。").toString();
        }
        return "当前为模拟模式，可测试：查询商户、查询火锅、第一家有什么优惠券、商户1详情、我的订单。数据通过真实MCP工具查询；自由对话需要切换真实模型。";
    }

    private String error(JsonNode result) { return result.path("errorMsg").asText("查询没有返回有效数据，请稍后重试。"); }
    private String money(JsonNode value) { return BigDecimal.valueOf(value.asLong()).movePointLeft(2).toPlainString(); }
}
