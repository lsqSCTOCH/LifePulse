# 代码使用说明(本项目来自b站[黑马程序员](https://space.bilibili.com/37974444)[redis教程](https://www.bilibili.com/video/BV1cr4y1671t)，仅供参考)

## 新增智能客服

已添加独立 Spring AI 客服服务、业务 MCP Server 和前端聊天页面。默认模拟模式无需模型 Key，查询真实业务数据；可切换 GPT-5.5 模式。
启动顺序、认证说明和验证方法见 [智能客服使用说明](ai-customer-service/README.md)。

项目代码包含2个分支：
- master : 主分支，包含完整版代码，作为大家的编码参考使用
- init (包含前端资源): 初始化分支，实战篇的初始代码，建议大家以这个分支作为自己开发的基础代码
  - 前端资源位于init分支src/main/resources/nginx-1.18.0下

视频地址:
- [黑马程序员Redis入门到实战教程，深度透析redis底层原理+redis分布式锁+企业解决方案+redis实战](https://www.bilibili.com/video/BV1cr4y1671t)
- [https://www.bilibili.com/video/BV1cr4y1671t](https://www.bilibili.com/video/BV1cr4y1671t)
  - P24起 实战篇

## 1.下载
克隆完整项目
```git
git clone https://github.com/cs001020/hmdp.git
```
切换分支
```git
git checkout init
```

## 2.常见问题
部分同学直接使用了master分支项目来启动，控制台会一直报错:
```
NOGROUP No such key 'stream.orders' or consumer group 'g1' in XREADGROUP with GROUP option
```
这是因为我们完整版代码会尝试访问Redis，连接Redis的Stream。建议同学切换到init分支来开发，如果一定要运行master分支，请先在Redis运行一下命令：
```text
XGROUP CREATE stream.orders g1 $ MKSTREAM
```

## IP城市定位与分类演示商户

新增 `/location/current`：后端通过 `https://ipwho.is/` 做城市级IP定位；本机访问时使用当前电脑网络出口IP，公网访问时使用客户端IP。代理或VPN可能影响城市，定位失败不会自动假定杭州，页面支持手动选择或重新定位。
Nginx覆盖 `X-Real-IP`，后端仅信任本机代理的该头部；若后续迁移到远程反向代理，需配置受信代理链。

本次升级给 `tb_shop` 增加了 `city` 字段，原始杭州商户保留。10个生活分类各新增3家带“【演示】”名称的店铺，总计30家；无真实地址、价格、评分或优惠券。城市列表按真实商户所属城市过滤，同时提供明确标记的演示店铺。
首页每分类展示最多3家商户，并提供“查看全部”。分类列表不再使用写死的杭州坐标。

数据库升级和演示店铺写入已执行，脚本见 `scripts/seed-demo-shops.sql`，可重复执行且不会覆盖原记录。
后端启动也会幂等补充字段和演示店铺；可配置 `app.demo-shops.enabled=false` 关闭后续自动补充（不删除已有数据）。

应用代码与Nginx配置更新后需重启8081后端并重载项目Nginx，浏览器强制刷新。
离线回归验证：

```bash
mvn -Dtest=IpLocationServiceTest,ShopCityQueryTest,CacheClientTest,VoucherOrderQueryTest test
```

外部IP定位服务的网络连通性与数据精度仍取决于运行环境；演示数据不能作为真实商户业务数据使用。


### 清水河校区周边商户

首页各分类展示成都电子科技大学清水河校区及龙湖成都时代天街周边的公开商户资料，与顶部的 IP 城市分开。点击分类进入校园精选，手动选择城市后可恢复按城市浏览。

- 商户目录：`src/main/resources/campus-shops.json`，当前 28 家，9 个分类各 3 家，轰趴馆仅 1 家有可核实资料。
- 启动时自动增加资料来源字段，并将原演示槽位替换为校园商户，保留其 ID。不改动原杭州商户及优惠券、订单、笔记关联。重复启动不会新增同一目录商户。
- `app.campus-shops.enabled=false` 可关闭目录初始化；`app.demo-shops.enabled` 默认关闭。
- `/shop/of/campus?typeId=1&current=1&sortBy=score` 返回校园精选；现有城市查询接口继续使用。
- 评分为来源页面查询快照，非平台实时评价。`score=0`、`avgPrice=0` 表示资料缺失，页面显示“暂无公开评分”“价格请咨询商户”。
- 商户照片使用对应公开相册并在详情提供来源。目前仅部分商户取得匹配图片，未取得本店实拍的使用龙湖官网的成都时代天街商圈实景，并在卡片和详情明确标注“非本店照片”。商圈图来自2020年公开资料，不作为当前门店营业状态的证明；下载失败时显示待补充。
- 图片地址及来源：`scripts/campus-photo-sources.json`；下载脚本：`python3 scripts/download-campus-photos.py`。下载完成后需重启后端以更新商户图片字段。
- 资料更新方式：修改目录中对应的 `catalogKey` 记录，重启后端即可同步。新增商户需使用独立 `catalogKey`，每分类的槽位编号目前为 1—3。

更新后重启根目录后端（8081）并强制刷新浏览器。客服关于资料缺失的提示需重启 `ai-customer-service` 后生效。
