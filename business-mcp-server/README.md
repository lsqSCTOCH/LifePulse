# LifePulse 业务 MCP Server

独立 JDK 17 / Spring Boot 3.5.11 / Spring AI 1.1.8 服务，默认本机8083，Streamable HTTP端点 `/mcp`。

提供 `searchShops`、`getShopDetail`、`listShopVouchers`、`getMyOrders`、`getMyOrderStatus` 五个只读工具，调用原有8081后端。
订单查询凭证由客服服务的可信上下文注入，再由原后端校验登录及订单归属。
服务仅绑定本机；部署到其他环境前需单独设计服务认证和凭证传递，不应直接公网暴露。

完整启动与验证步骤参见 [客服说明](../ai-customer-service/README.md)。
