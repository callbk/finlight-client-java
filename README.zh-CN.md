# finlight-client (JVM)

*[English](README.md) | 简体中文 | [日本語](README.ja.md) | [한국어](README.ko.md)*

[![Maven Central](https://img.shields.io/maven-central/v/me.finlight/finlight-client)](https://central.sonatype.com/artifact/me.finlight/finlight-client)
[![JitPack](https://jitpack.io/v/callbk/finlight-client-java.svg)](https://jitpack.io/#callbk/finlight-client-java)
[![CI](https://github.com/callbk/finlight-client-java/actions/workflows/test.yml/badge.svg)](https://github.com/callbk/finlight-client-java/actions/workflows/test.yml)

[finlight.me](https://finlight.me) API 的官方 JVM 客户端 —— 提供带情感分析、实体识别和实时流式推送的财经新闻。使用 Java 17 编写，并针对 Kotlin 互操作做了一流设计（JSpecify null-marked API、不可变 record、builder）。

📚 **完整 API 文档：[docs.finlight.me](https://docs.finlight.me)**

## 功能特性

- **REST API**：检索文章、按链接获取单篇文章、列出新闻源
- **实时流式推送**：通过 WebSocket 提供 enhanced 和 raw 两种文章流
- **默认具备韧性**：请求失败按指数退避重试；WebSocket 自动重连、带 pong 看门狗的保活、主动连接轮换，以及速率限制处理
- **支持 Webhook**：HMAC-SHA256 签名验证，含重放防护
- **依赖极少**：JSON 使用 Jackson，日志使用 SLF4J（自行提供 binding），HTTP/WebSocket 使用 JDK 内置客户端

需要 Java 17 及以上版本。

## 安装

Gradle（Kotlin DSL）：

```kotlin
dependencies {
    implementation("me.finlight:finlight-client:0.1.0")
}
```

Maven：

```xml
<dependency>
    <groupId>me.finlight</groupId>
    <artifactId>finlight-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

也可以通过 [JitPack](https://jitpack.io/#callbk/finlight-client-java) 直接从 GitHub tag 构建：

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.callbk:finlight-client-java:v0.1.0")
}
```

## 快速开始

### Java

```java
import me.finlight.client.FinlightClient;
import me.finlight.client.model.ArticleResponse;
import me.finlight.client.model.GetArticlesParams;

FinlightClient client = FinlightClient.create(System.getenv("FINLIGHT_API_KEY"));

ArticleResponse response = client.articles().fetchArticles(
    GetArticlesParams.builder()
        .query("nvidia")
        .pageSize(10)
        .build());

response.articles().forEach(article ->
    System.out.printf("[%s] %s%n", article.source(), article.title()));
```

### Kotlin

```kotlin
import me.finlight.client.FinlightClient
import me.finlight.client.model.GetArticlesParams

val client = FinlightClient.create(System.getenv("FINLIGHT_API_KEY"))

val response = client.articles().fetchArticles(
    GetArticlesParams.builder()
        .query("nvidia")
        .pageSize(10)
        .build()
)

response.articles().forEach { println("[${it.source()}] ${it.title()}") }
```

## REST API

### 检索文章

完整的 `query` 语法参见[查询语言参考](https://docs.finlight.me)。

```java
ArticleResponse response = client.articles().fetchArticles(
    GetArticlesParams.builder()
        .query("(ticker:AAPL OR ticker:NVDA) AND \"Elon Musk\"")
        .tickers(List.of("AAPL", "NVDA"))
        .from("2025-01-01")
        .includeContent(true)
        .includeEntities(true)
        .orderBy(OrderBy.PUBLISH_DATE)
        .order(SortOrder.DESC)
        .pageSize(20)
        .build());
```

### 按链接获取单篇文章

```java
Article article = client.articles().fetchArticleByLink(
    GetArticleByLinkParams.builder("https://www.reuters.com/technology/example")
        .includeContent(true)
        .build());
```

### 列出新闻源

```java
List<Source> sources = client.sources().getSources();
```

## 实时流式推送

`connect` 会阻塞并自动重连，直到你调用 `stop()`；使用 `connectAsync` 可将其运行在后台线程中。enhanced 流推送带情感和实体的文章；raw 流（`client.rawWebSocket()`）以最低延迟推送未经增强的文章。

```java
FinlightClient client = FinlightClient.create(apiKey);

client.webSocket().connect(
    GetArticlesWebSocketParams.builder()
        .tickers(List.of("AAPL", "NVDA"))
        .includeContent(true)
        .build(),
    article -> System.out.println(article.title()));
```

自定义流选项：

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.of(apiKey),
    WebSocketOptions.builder()
        .takeover(true) // 接管同一密钥下已有的连接
        .onClose((code, reason) -> System.out.println("closed: " + code))
        .build());
```

若服务端永久拒绝该连接（关闭码 1008），`connect` 会抛出 `FinlightBlockedException` —— 此时重连无济于事，请联系支持。

## Webhook

使用原始（未解析的）请求体验证收到的 Webhook：

```java
import me.finlight.client.WebhookService;
import me.finlight.client.WebhookVerificationException;

// 例如在 Spring controller 中
@PostMapping("/webhook")
public ResponseEntity<Void> onWebhook(
    @RequestBody String rawBody,
    @RequestHeader("X-Webhook-Signature") String signature,
    @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp) {
  try {
    Article article = WebhookService.constructEvent(rawBody, signature, webhookSecret, timestamp);
    log.info("New article: {}", article.title());
    return ResponseEntity.ok().build();
  } catch (WebhookVerificationException e) {
    return ResponseEntity.badRequest().build();
  }
}
```

## 配置

| 选项         | 默认值                    | 说明                                            |
| ------------ | ------------------------- | ----------------------------------------------- |
| `baseUrl`    | `https://api.finlight.me` | REST 接口地址                                   |
| `wssUrl`     | `wss://wss.finlight.me`   | WebSocket 接口地址                              |
| `timeout`    | 5s                        | 单次请求和连接超时                              |
| `retryCount` | 3                         | 可重试失败（429/5xx）的总尝试次数               |
| `httpClient` | 内置                      | 自定义 `java.net.http.HttpClient`（例如走代理） |

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.builder(apiKey)
        .timeout(Duration.ofSeconds(10))
        .retryCount(5)
        .build());
```

日志通过 [SLF4J](https://www.slf4j.org/) 输出；添加一个 binding（Logback、`slf4j-simple` 等）即可看到客户端日志，logger 命名空间为 `me.finlight.client`。

## 开发

```bash
./gradlew build           # 编译、静态检查（Spotless + Error Prone）、测试
./gradlew spotlessApply   # 修复代码格式
```

### 针对真实 API 的验证

`local/` 目录已被 gitignore，用于存放冒烟和长跑测试脚本，与 Go 和 .NET 客户端保持一致 —— 凭据请放在环境变量中，切勿写入代码。

```bash
# 一次性冒烟测试：REST 接口 + 30 秒文章流
FINLIGHT_API_KEY=sk_... ./gradlew -q smoke

# 长时间流稳定性测试（默认 10 分钟）
FINLIGHT_API_KEY=sk_... SOAK_MINUTES=60 ./gradlew -q soak

# REST 集成测试（未设置密钥时自动跳过）
FINLIGHT_API_KEY=sk_... ./gradlew test --tests '*IntegrationSmokeTest'
```

可选：使用 `FINLIGHT_BASE_URL` / `FINLIGHT_WSS_URL` 指向 dev 环境。

## 许可证

[MIT](LICENSE)

## 相关资源

- 中文产品页：https://finlight.me/zh/news-api
