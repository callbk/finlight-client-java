# finlight-client (JVM)

The official JVM client for the [finlight.me](https://finlight.me) API — financial news with sentiment analysis, entity recognition, and real-time streaming. Written in Java 17, designed for first-class Kotlin interop (JSpecify null-marked API, immutable records, builders).

📚 **Full API documentation: [docs.finlight.me](https://docs.finlight.me)**

## Features

- **REST API**: search articles, fetch single articles by link, list sources
- **Real-time streaming**: enhanced and raw article streams over WebSocket
- **Resilient by default**: request retries with exponential backoff; WebSocket auto-reconnect, keepalive with pong watchdog, proactive connection rotation, and rate-limit handling
- **Webhook support**: HMAC-SHA256 signature verification with replay protection
- **Minimal dependencies**: Jackson for JSON, SLF4J for logging (bring your own binding), JDK built-in HTTP/WebSocket client

Requires Java 17+.

## Installation

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("me.finlight:finlight-client:0.1.0")
}
```

Maven:

```xml
<dependency>
    <groupId>me.finlight</groupId>
    <artifactId>finlight-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick Start

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

### Search articles

See the [query language reference](https://docs.finlight.me) for the full `query` syntax.

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

### Fetch a single article by link

```java
Article article = client.articles().fetchArticleByLink(
    GetArticleByLinkParams.builder("https://www.reuters.com/technology/example")
        .includeContent(true)
        .build());
```

### List sources

```java
List<Source> sources = client.sources().getSources();
```

## Real-time streaming

`connect` blocks and reconnects automatically until you call `stop()`; use `connectAsync` to run it on a background thread. The enhanced stream delivers articles with sentiment and entities; the raw stream (`client.rawWebSocket()`) delivers unenriched articles with minimal latency.

```java
FinlightClient client = FinlightClient.create(apiKey);

client.webSocket().connect(
    GetArticlesWebSocketParams.builder()
        .tickers(List.of("AAPL", "NVDA"))
        .includeContent(true)
        .build(),
    article -> System.out.println(article.title()));
```

Custom stream options:

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.of(apiKey),
    WebSocketOptions.builder()
        .takeover(true) // take over an existing connection for the same key
        .onClose((code, reason) -> System.out.println("closed: " + code))
        .build());
```

If the server permanently rejects the connection (close code 1008), `connect` throws `FinlightBlockedException` — reconnecting will not help; contact support.

## Webhooks

Verify incoming webhooks with the raw (unparsed) request body:

```java
import me.finlight.client.WebhookService;
import me.finlight.client.WebhookVerificationException;

// e.g. in a Spring controller
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

## Configuration

| Option       | Default                   | Description                                     |
| ------------ | ------------------------- | ----------------------------------------------- |
| `baseUrl`    | `https://api.finlight.me` | REST endpoint                                   |
| `wssUrl`     | `wss://wss.finlight.me`   | WebSocket endpoint                              |
| `timeout`    | 5s                        | Per-request and connect timeout                 |
| `retryCount` | 3                         | Total attempts for retryable failures (429/5xx) |
| `httpClient` | built-in                  | Custom `java.net.http.HttpClient` (e.g. proxy)  |

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.builder(apiKey)
        .timeout(Duration.ofSeconds(10))
        .retryCount(5)
        .build());
```

Logging goes through [SLF4J](https://www.slf4j.org/); add a binding (Logback, `slf4j-simple`, …) to see client logs, e.g. under the `me.finlight.client` logger namespace.

## Development

```bash
./gradlew build           # compile, lint (Spotless + Error Prone), test
./gradlew spotlessApply   # fix formatting
```

### Verification against the real API

The `local/` directory is gitignored and holds smoke/soak runners, mirroring the Go and .NET clients — keep credentials in the environment, never in code.

```bash
# One-shot smoke: REST endpoints + 30s article stream
FINLIGHT_API_KEY=sk_... ./gradlew -q smoke

# Long-running stream stability (default 10 minutes)
FINLIGHT_API_KEY=sk_... SOAK_MINUTES=60 ./gradlew -q soak

# REST integration tests (auto-skipped when the key is not set)
FINLIGHT_API_KEY=sk_... ./gradlew test --tests '*IntegrationSmokeTest'
```

Optional: `FINLIGHT_BASE_URL` / `FINLIGHT_WSS_URL` to target dev.

## License

[MIT](LICENSE)
