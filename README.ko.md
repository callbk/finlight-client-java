# finlight-client (JVM)

*[English](README.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | 한국어*

[![Maven Central](https://img.shields.io/maven-central/v/me.finlight/finlight-client)](https://central.sonatype.com/artifact/me.finlight/finlight-client)
[![JitPack](https://jitpack.io/v/callbk/finlight-client-java.svg)](https://jitpack.io/#callbk/finlight-client-java)
[![CI](https://github.com/callbk/finlight-client-java/actions/workflows/test.yml/badge.svg)](https://github.com/callbk/finlight-client-java/actions/workflows/test.yml)

[finlight.me](https://finlight.me) API의 공식 JVM 클라이언트입니다. 감성 분석, 엔티티 인식, 실시간 스트리밍을 갖춘 금융 뉴스를 제공합니다. Java 17로 작성되었으며 Kotlin 상호 운용을 일급으로 고려해 설계했습니다(JSpecify null-marked API, 불변 record, 빌더).

📚 **전체 API 문서: [docs.finlight.me](https://docs.finlight.me)**

## 주요 기능

- **REST API**: 기사 검색, 링크로 단일 기사 조회, 뉴스 소스 목록 조회
- **실시간 스트리밍**: WebSocket을 통한 enhanced 및 raw 기사 스트림
- **기본적으로 견고함**: 지수 백오프를 적용한 요청 재시도, WebSocket 자동 재연결, pong 워치독 기반 킵얼라이브, 선제적 연결 교체, 속도 제한 처리
- **Webhook 지원**: HMAC-SHA256 서명 검증 및 재생 공격 방지
- **최소한의 의존성**: JSON은 Jackson, 로깅은 SLF4J(바인딩은 사용자가 준비), HTTP/WebSocket은 JDK 내장 클라이언트

Java 17 이상이 필요합니다.

## 설치

Gradle(Kotlin DSL):

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

또는 [JitPack](https://jitpack.io/#callbk/finlight-client-java)을 통해 GitHub 태그에서 직접 빌드할 수도 있습니다:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.callbk:finlight-client-java:v0.1.0")
}
```

## 빠른 시작

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

### 기사 검색

`query`의 전체 문법은 [쿼리 언어 레퍼런스](https://docs.finlight.me)를 참조하세요.

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

### 링크로 단일 기사 조회

```java
Article article = client.articles().fetchArticleByLink(
    GetArticleByLinkParams.builder("https://www.reuters.com/technology/example")
        .includeContent(true)
        .build());
```

### 뉴스 소스 목록 조회

```java
List<Source> sources = client.sources().getSources();
```

## 실시간 스트리밍

`connect`는 블로킹되며 `stop()`을 호출할 때까지 자동으로 재연결합니다. 백그라운드 스레드에서 실행하려면 `connectAsync`를 사용하세요. enhanced 스트림은 감성과 엔티티가 포함된 기사를, raw 스트림(`client.rawWebSocket()`)은 최소 지연으로 보강되지 않은 기사를 전달합니다.

```java
FinlightClient client = FinlightClient.create(apiKey);

client.webSocket().connect(
    GetArticlesWebSocketParams.builder()
        .tickers(List.of("AAPL", "NVDA"))
        .includeContent(true)
        .build(),
    article -> System.out.println(article.title()));
```

스트림 옵션 사용자 지정:

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.of(apiKey),
    WebSocketOptions.builder()
        .takeover(true) // 동일한 키의 기존 연결을 테이크오버
        .onClose((code, reason) -> System.out.println("closed: " + code))
        .build());
```

서버가 연결을 영구적으로 거부하면(클로즈 코드 1008) `connect`는 `FinlightBlockedException`을 던집니다. 재연결해도 해결되지 않으므로 지원팀에 문의하세요.

## Webhook

원본(파싱되지 않은) 요청 본문으로 수신한 Webhook을 검증합니다:

```java
import me.finlight.client.WebhookService;
import me.finlight.client.WebhookVerificationException;

// 예: Spring controller에서
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

## 설정

| 옵션         | 기본값                    | 설명                                            |
| ------------ | ------------------------- | ----------------------------------------------- |
| `baseUrl`    | `https://api.finlight.me` | REST 엔드포인트                                 |
| `wssUrl`     | `wss://wss.finlight.me`   | WebSocket 엔드포인트                            |
| `timeout`    | 5s                        | 요청당 및 연결 타임아웃                         |
| `retryCount` | 3                         | 재시도 대상 실패(429/5xx)에 대한 총 시도 횟수   |
| `httpClient` | 내장                      | 사용자 정의 `java.net.http.HttpClient`(프록시 등) |

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.builder(apiKey)
        .timeout(Duration.ofSeconds(10))
        .retryCount(5)
        .build());
```

로깅은 [SLF4J](https://www.slf4j.org/)를 통해 이루어집니다. 클라이언트 로그를 보려면 바인딩(Logback, `slf4j-simple` 등)을 추가하세요. 로거 네임스페이스는 `me.finlight.client`입니다.

## 개발

```bash
./gradlew build           # 컴파일, 정적 검사(Spotless + Error Prone), 테스트
./gradlew spotlessApply   # 포매팅 수정
```

### 실제 API 대상 검증

`local/` 디렉터리는 gitignore되어 있으며 스모크 및 소크 실행 스크립트를 담고 있습니다(Go 및 .NET 클라이언트와 동일한 구성). 자격 증명은 환경 변수에 두고 코드에는 절대 넣지 마세요.

```bash
# 일회성 스모크: REST 엔드포인트 + 30초 기사 스트림
FINLIGHT_API_KEY=sk_... ./gradlew -q smoke

# 장시간 스트림 안정성 테스트(기본 10분)
FINLIGHT_API_KEY=sk_... SOAK_MINUTES=60 ./gradlew -q soak

# REST 통합 테스트(키가 설정되지 않으면 자동 건너뜀)
FINLIGHT_API_KEY=sk_... ./gradlew test --tests '*IntegrationSmokeTest'
```

선택 사항: dev 환경을 대상으로 하려면 `FINLIGHT_BASE_URL` / `FINLIGHT_WSS_URL`을 지정하세요.

## 라이선스

[MIT](LICENSE)

## 관련 링크

- 한국어 제품 페이지: https://finlight.me/ko/news-api
