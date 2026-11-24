# finlight Java 与 Kotlin 客户端

*[English](README.md) | 简体中文 | [日本語](README.ja.md) | [한국어](README.ko.md)*

> 本译文根据英文版 README 编写，内容可能稍有滞后；如有出入，以 [README.md](README.md) 为准。

> **不兼容的大版本。** 1.0.0 只支持 finlight API v3。为 0.1.x 编写的代码无法在 1.x 上编译。若要继续使用
> API v2，请保留 `me.finlight:finlight-client:0.1.0`；0.1.x 不再发布新版本。详见
> [从 0.1.x 迁移](README.md#migrating-from-01x)。

[finlight](https://finlight.me) 新闻 API 的 JVM 客户端：通过 REST 搜索和查询财经新闻、公司与来源，通过 WebSocket
实时接收文章，并验证 Webhook。以 Java 17 编写；Kotlin 使用同一套 API（JSpecify 空值注解、Builder、sealed 类型）。

## 版本

<<TABLE 0>>

SDK 版本与 API 版本相互独立：服务端新增的字段和取值会以兼容的 1.x 版本发布。其他支持 API v3 的 finlight SDK：
TypeScript 4.x、Python 3.x、PHP 2.x、Go 模块 `/v2`、.NET 2.x、Java 1.x、Ruby 1.x、Rust 0.2。

## 运行要求

- Java 17 及以上（在 17 和 21 上测试）
- Jackson `jackson-databind` 2.13.5 及以上（以 2.13.5 和 2.18.3 测试）。发布的 POM 以 2.13.5 为下限，且不导入 Jackson BOM，
  你的构建保留自己的 Jackson 版本。
- JSpecify 1.0（仅注解）

REST 与 WebSocket 使用 JDK 自带的 `java.net.http` 客户端，没有其他运行时依赖。

## 安装

Gradle（Kotlin DSL）：

<<CODE 0>>

Maven：

<<CODE 1>>

## 快速开始

<<CODE 2>>

Kotlin：

<<CODE 3>>

REST 调用会阻塞当前线程。每个 Builder 方法对应一个 API 键名；只发送你设置过的键。

## 搜索文章

<<CODE 4>>

`sourceTypes` 和 `excludeSourceTypes` 按来源类型过滤（`SourceType`：`newswire`、`press_release`、`financial_media`、
`general_media`、`aggregator`、`blog`、`government`）。未分类来源的文章不会匹配 `sourceTypes`，在 `excludeSourceTypes` 下则会保留。
两者都只在你的来源池内过滤，不会添加来源。未分类来源的 `source().type()` 和 `Source.type()` 为 null。

`searchAll` 会自动翻页。调用时请求体即被冻结，之后的页只追加 cursor。为节省配额，最多翻 `maxPages` 页（默认 10）。

<<CODE 5>>

结果只能遍历一次。形状错误（例如 `limit(0)` 或空的 `languages`）在发送前抛出 `FinlightValidationException`；
未知的键无法通过编译。词表取值不在本地校验，因此服务端的新取值无需升级 SDK。日期以带毫秒的 UTC 发送
（`2026-11-01T00:00:00.000Z`），纯日期为 `YYYY-MM-DD`，字符串原样发送。

## 查询文章、公司和来源

<<CODE 6>>

- 查不到时抛出 `NotFoundException`（404 是错误，不是空结果）。在 id 推出之前收录的文章只能通过链接查询。
- 公司查询需要 `entityAccess` 权限（否则为 `ForbiddenException`）。
- 公司和来源查询与文章请求一样，每个请求消耗月度配额 1 个额度（含多个 ID 的 `getMany` 也算 1 个请求）。
  304 重新验证和错误也消耗 1 个额度。
- `companies().get(id)` 会缓存记录及其 ETag 并重新验证；未变化时从缓存返回，`cacheInfo().notModified()` 为 true。
  使用自己的 `ifNoneMatch` 时，304 返回 null。
- 列表（`CompanyList`、`SourceList`、`ArticlePage.articles()`）会跳过无法读取的条目，记录警告，并将其索引与原始 JSON
  保存在 `skipped()` 中。

## 缺失、null 与空

取决于套餐或 `include` 的键有三种状态，由 `Gated<T>` 表示：

<<TABLE 1>>

<<CODE 7>>

`content` 与 `sentiment` 可以是缺失、null 或有值。`companies`、`events`、`events[].companyIds` 与 `Source.hasContent`
要么缺失要么有值（`[]` 也是值）。只有同一负载中包含 `companies` 时才会发送 `companyIds`。缺失永远不会被转换为 `[]`、
`false` 或 `0`。

时间戳保持原始字符串（`publishedAt()`）；`publishedAtTime()` 等 `*Time()` 方法按 RFC 3339 严格解析，无法读取时返回 null。
新版服务端的未知键会被忽略；`Article`、`CompanyRecord`、`Source`、`StreamEvent`、`WebhookEvent` 和 API 错误上的
`rawJson()` 返回收到的 JSON。词表取值是开放的：请与常量比较（`CompanyRole.ACQUIRER`，在 `switch` 中用
`EventTypeValues.MNA`），未知取值原样保留。

## 实时流

<<CODE 8>>

Kotlin 可以为 `onEvent` 传入 lambda：`client.stream(params) { e -> println(e.event()) }`。

- `client.rawStream(RawStreamParams, listener)` 推送未经增强的文章（`<wssUrl>/raw`）：没有 `include`、content、
  sentiment、companies 或 events，`availableAt` 始终为 null。
- `sourceTypes` 和 `excludeSourceTypes` 在两种流上的作用与搜索相同。只有在你设置时 SDK 才会发送。
  服务器不认识的值会以 `ValidationFailedException` 结束流（字段 `sourceTypes`，允许的值在 `errors()` 中）。
- 监听器方法按顺序在流的一个守护线程上运行。慢速监听器不会触发保活超时；其中的异常会被记录，流继续运行。
  `close()` 之后不再收到事件；最后调用 `onClosed`。`stream.awaitTermination()` 会阻塞到结束。
- 流每 25 秒发送 ping，60 秒内无 pong 则替换连接，115 分钟后更新连接，每次重连后发送相同的订阅。重复项会被丢弃；
  同一文章的 `article.created` 与 `article.updated` 是两个事件。
- SDK 不会自动补数据。`onReconnect` 提供你最后看到的 `availableAt`（raw：`firstSeenAt`）。如需补齐，请从该时间起以
  `orderBy` `availableAt`（raw：`firstSeenAt`）调用 `searchAll`。
- 终止性问题通过 `onClosed(terminal)` 结束流：无效密钥（`UnauthorizedException`）、无 WebSocket 权限
  （`ForbiddenException`）、订阅被拒（`ValidationFailedException`），以及其他连接接管时原因为 `connection_limit` 的
  `PreemptedException`。其他问题会重连并交给 `onError`。
- 当月 WebSocket 配额用尽（连接以 "quota exceeded" 被拒，或 `PreemptedException` 原因为 `quota_exceeded`）时，流不会结束：
  每小时重试一次，直到配额允许。每次等待都会记录警告并调用 `onQuotaWait`。设置 `quotaReconnect(false)` 则以错误结束流；
  `quotaReconnectInterval` 可修改间隔。

需要了解的限制：

- 套餐的连接数上限由增强流、raw 流和旧版 SDK 连接共享。达到上限时连接被拒，SDK 在 60 秒后重试。
  `StreamOptions.takeover(true)` 会改为替换你最久未活动的连接。
- 每次订阅后，服务端会先发送一次最新的匹配文章，它可能是旧文章。
- 网关在 2 小时后结束每个连接；SDK 会提前更新。
- 超过 32,000 字节的实时帧会被服务端静默丢弃。对长文章使用 content 时可能发生。请用 `articles().get(id)` 获取。
- `connection.admitted`（`onAdmitted`）在服务端检查订阅之前到达，并不代表订阅已被接受。

## Webhook

finlight 会对每个 Webhook 签名。请在任何框架解析之前，用**原始**请求体验证：

<<CODE 9>>

- `headers` 可以是任何 `Map<String, ?>`，值为字符串、集合或数组（Servlet、Spring、JDK
  `HttpExchange.getRequestHeaders()`）。名称不区分大小写。重载 `constructEvent(body, signature, timestamp, secret)`
  直接接收两个请求头的值；`verifySignature` 只做校验。
- 时间戳必须在双向 300 秒（`WebhookVerifyOptions.tolerance`）以内。
- 仪表盘测试 Webhook 的 `event.isTest()` 为 true，不受套餐裁剪。
- 永远不要返回 410：它会立即停用 Webhook。连续 10 次非 2xx 也会停用。

获取原始请求体：

<<TABLE 2>>

将 Webhook 切换到 v3：先部署本 SDK；对 `WrongPayloadVersion` 返回 2xx 并保留其 `raw()`；然后在仪表盘中将 Webhook 切换到
`payloadVersion` 3；再等待约 2 小时，因为切换前排队的重试仍会以旧格式送达。

## 错误与重试

所有错误都继承 `FinlightException`（`RuntimeException`），并提供 `kind()`：

<<CODE 10>>

- 5xx、网络错误和单次尝试超时最多重试 3 次，等待时间递增并带随机抖动，POST 搜索也包括在内。
- 400、401、403 和 404 从不重试。当月 429 会立即失败，`searchAll` 中也是如此：REST 从不等待新的配额周期（只有流会等待）。
- 突发 429 会精确等待 `Retry-After`（没有该头时为 10 秒）后再试一次。默认开启，不计入 `maxRetries`；
  `retryOnBurst(RetryOnBurst.OFF)` 会立即抛出。
- 每次调用发送自己的 `X-Request-Id`，重试时保持不变；`requestId()` 携带该值。
- JDK 的 `HttpClient` 在无法建立连接或连接池中的连接已关闭时，会自行重发 GET。这种重发保留 `X-Request-Id`，不计入
  `maxRetries`。`-Djdk.httpclient.disableRetryConnect=true` 可关闭连接失败后的重发（对整个 JVM 生效）；连接池连接已关闭时的重发无法关闭。
- 中断调用线程即可取消调用：等待立即结束，不再重试，调用抛出 `java.util.concurrent.CancellationException`，并恢复中断标志。

## 配置

<<CODE 11>>

<<TABLE 3>>

`httpClient(java.net.http.HttpClient)` 将你的客户端用于 REST 和 WebSocket（代理、TLS、执行器）。其连接超时和代理设置生效；
SDK 从不关闭它。不要再包一层自己的重试：每次尝试都消耗配额。JDK 自身的 GET 重发（见"错误与重试"）同样适用。`client.close()` 会结束该客户端的所有流，并关闭 SDK 自己创建的
HTTP 客户端。

## 日志

SDK 通过平台日志器 `System.getLogger("finlight")` 记录日志（默认是 java.util.logging，除非你安装了其他后端，例如
`slf4j-jdk-platform-logging`）。细节记为 DEBUG，问题记为 WARNING；默认不输出其他内容，SDK 也从不直接写 stdout 或 stderr。
API 密钥、Webhook 密钥、签名和请求体从不出现在日志或错误信息中，`FinlightConfig.toString()` 会隐藏密钥。

## 数据说明

- 旧文章的 `publishedAt` 可能是 `"1970-01-01T00:00:00.000Z"`，按原样交付。
- 空文本以 null 交付（`summary`、`content`），从不为 `""`。
- 在角色功能之前标注的文章，其 `role` 和 `relevance` 为 null，`events` 为 `[]`。
- 对在 id 推出之前收录的文章，`articles().get(id)` 返回 404。请使用 `getByLink`。
- 较旧文章的 `availableAt` 为 null，raw 流中始终为 null。

## 从 0.1.x 迁移

完整指南见 [docs.finlight.me](https://docs.finlight.me)。简要对照：

<<TABLE 4>>

0.1.x 与 1.x 使用相同的包名，不能同时位于一个 classpath。也不要在同一密钥上同时运行 0.1.x 与 1.x 的流：它们共享连接上限。

切换 Webhook：部署 1.x，对 `WrongPayloadVersion` 返回 2xx 并保留 `raw()`，在仪表盘中将 Webhook 切换到 `payloadVersion` 3，
然后等待约 2 小时，让排队的重试送达。

## 开发

<<CODE 12>>

契约套件（`conformance/`，版本见 `conformance/CONTRACT_VERSION`）随仓库提供，并按其 sha256 `MANIFEST` 校验。
`CONFORMANCE.md` 将每个套件用例映射到对应测试。`examples/` 中的五个示例由测试套件编译并在本地模拟服务上运行。
实时检查只针对 dev 环境，不属于 `./gradlew test`，需要 `FINLIGHT_API_KEY`、`FINLIGHT_BASE_URL` 和 `FINLIGHT_WSS_URL`：
`./gradlew liveTest`。

## 许可证

MIT
