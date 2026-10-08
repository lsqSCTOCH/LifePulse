"""验证模拟客服的真实 MCP 链路；不调用大模型，不修改业务数据。"""
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request

base = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8082"


def client():
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
    )


def chat(opener, message, conversation=None):
    request = urllib.request.Request(
        base + "/ai/chat",
        data=json.dumps({"message": message, "conversationId": conversation}).encode(),
        headers={"Content-Type": "application/json"},
    )
    try:
        with opener.open(request, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


opener = client()
health = json.load(opener.open(base + "/ai/health", timeout=5))
assert health["mode"] == "mock", "请在模拟模式执行，避免调用真实模型"
assert len(health["tools"]) == 5
status, shops = chat(opener, "查询商户")
assert status == 200 and shops["success"]
assert shops["tools"][0]["result"]["data"], "数据库应有商户数据"
first = str(shops["tools"][0]["result"]["data"][0]["id"])
conversation = shops["conversationId"]
status, vouchers = chat(opener, "第一家有什么优惠券", conversation)
assert status == 200 and vouchers["success"]
assert vouchers["tools"][0]["arguments"]["shopId"] == first
assert vouchers["tools"][0]["result"]["success"]
status, orders = chat(opener, "我的订单", conversation)
assert status == 200 and "登录" in orders["reply"]
assert "accessToken" not in json.dumps(orders)
status, _ = chat(client(), "第一家详情", conversation)
assert status == 404, "不同浏览器会话必须隔离"
status, fresh = chat(opener, "第一家有什么优惠券")
assert status == 200 and not fresh["tools"], "新会话不应带入旧商户"
request = urllib.request.Request(base + "/ai/conversations/" + conversation, method="DELETE")
assert json.load(opener.open(request))["success"]
status, _ = chat(opener, "第一家详情", conversation)
assert status == 404
print("PASS: MCP发现、真实商户/优惠券查询、多轮引用、游客订单、会话隔离及清理")
