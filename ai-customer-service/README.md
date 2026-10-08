# LifePulse 智能客服

当前已实现独立 MCP Server、Spring AI 客服服务、会话隔离和前端聊天页面。
默认模拟模式不使用 API Key，不调用中转平台；通过预设规则选择真正的 MCP 工具，查询现有 MySQL/Redis 业务数据。
真实模式使用 Spring AI ChatClient、工具回调和 MessageChatMemoryAdvisor。真实中转平台调用尚未验证成功。

## 服务关系

- 8080：Nginx 页面；`/api/` 转发8081，`/ai/` 转发8082。
- 8081：原有业务后端，补充 `/voucher-order/mine` 和 `/voucher-order/mine/{id}`。
- 8082：客服服务，默认模拟模式。
- 8083：MCP Server，Streamable HTTP 协议，端点 `/mcp`。

所有新增服务使用 JDK 17，独立 POM 为 Spring Boot 3.5.11、Spring AI 1.1.8；原后端保持 Boot 2.7.4。

## 启动（每个命令在项目根目录独立终端执行）

每个 Java 终端先设置：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

1. 保持 MySQL、Redis 运行，重启原有业务后端以加载订单接口：

```bash
mvn spring-boot:run
```

2. 启动 MCP Server，等出现 `Started BusinessMcpApplication`：

```bash
mvn -f business-mcp-server/pom.xml spring-boot:run
```

3. 原客服终端 Ctrl+C 后启动模拟客服（不需要 Key）：

```bash
export AI_MOCK=true
mvn -f ai-customer-service/pom.xml spring-boot:run
```

MCP服务必须先启动，因为客服启动时会发现并注册工具。

4. 已运行的项目 Nginx 重新加载配置：

```bash
nginx -p /Users/momo/Projects/hmdp-master/frontend/ -c conf/nginx.conf -s reload
```

如果 Nginx 尚未启动，去掉 `-s reload`。

打开 http://localhost:8080/customer-service.html ，首页也增加了客服入口。

## 功能验证

依次发送：

1. `查询商户` → 真实查询商户第一页。
2. `第一家有什么优惠券` → 使用上一轮商户ID查询优惠券，金额由分转换为元。
3. `第二家详情` → 查询对应商户详情。
4. `我的订单` → 游客收到登录提示；登录后只查询本人的最近10笔订单。
5. `订单123` → 查询指定的本人订单。
6. 点击新建会话 → 不再保留上一轮商户选择。

页面显示模拟/模型模式、等待状态和可展开的业务工具调用记录；业务数据只通过 textContent 显示。
模拟模式仅识别上述预设表达和有限的名称关键词，不具备大模型的自由语言理解能力。

```bash
curl http://localhost:8082/ai/health
python3 ai-customer-service/scripts/smoke_test.py
```

测试脚本仅支持模拟模式，验证真实 MCP 发现、商户/优惠券链路、游客订单提示、不同浏览器会话隔离和会话清理。
若用 curl 手动多轮测试，须通过 `-c` / `-b` 保留 Cookie，同时传入返回的 conversationId。

## 认证与会话

- 前端沿用 sessionStorage 中的登录 token，通过 authorization 请求头传递。
- 客服会校验 `/user/me`，会话归属由浏览器会话和已认证用户共同决定。
- 订单凭证从可信请求上下文注入 MCP 调用，模型可见的工具 schema 中不包含凭证参数。
- 业务后端在 SQL 中同时限制订单ID和当前登录用户ID；模型和请求中的身份参数不能绕过该条件。
- 订单ID按字符串返回，避免 JavaScript 大整数精度损失；查询记录不包含登录凭证。
- 会话保存在内存，最多1000个，30分钟闲置后在后续访问时清理；模型上下文最多20条消息。
- 服务重启会丢失历史；多实例共享与长期历史需要后续接入持久化存储。
- 单轮最多8次工具调用；所有业务工具为只读，不执行购买或退款。

## 切换真实模型

在客服终端 Ctrl+C 后：

```bash
export AI_MOCK=false
read -rs 'OPENAI_API_KEY?请输入 API Key（输入不显示）：'
export OPENAI_API_KEY
mvn -f ai-customer-service/pom.xml spring-boot:run
```

默认模型 `gpt-5.5`，请求地址 `https://geekspace.cloud/v1/chat/completions`。
`AI_BASE_URL` 默认 `https://geekspace.cloud`，不含 `/v1`；路径由 completions-path 配置。
可用环境变量 `AI_MODEL`、`MCP_BASE_URL`、`BUSINESS_BASE_URL` 覆盖模型及服务地址。
Key 不写入源码、YAML或前端。当前连接超时10秒、读取超时45秒，不自动重试。
API权限、额度、平台兼容性及真实模型的工具选择和多轮效果仍需实测。

## 开发验证

```bash
mvn -f ai-customer-service/pom.xml test
mvn -Dtest=VoucherOrderQueryTest test
```

第一组使用本地模拟模型响应，不调用中转平台；验证API请求、输入校验、凭证隐藏及会话隔离/过期。
第二组验证未登录拦截、订单归属查询条件和大整数ID，使用模拟 Mapper，不访问数据库。
