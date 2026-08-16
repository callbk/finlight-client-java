# finlight-client (JVM)

*[English](README.md) | [简体中文](README.zh-CN.md) | 日本語 | [한국어](README.ko.md)*

[![Maven Central](https://img.shields.io/maven-central/v/me.finlight/finlight-client)](https://central.sonatype.com/artifact/me.finlight/finlight-client)
[![JitPack](https://jitpack.io/v/callbk/finlight-client-java.svg)](https://jitpack.io/#callbk/finlight-client-java)
[![CI](https://github.com/callbk/finlight-client-java/actions/workflows/test.yml/badge.svg)](https://github.com/callbk/finlight-client-java/actions/workflows/test.yml)

[finlight.me](https://finlight.me) API の公式 JVM クライアントです。センチメント分析、エンティティ認識、リアルタイムストリーミングを備えた金融ニュースを提供します。Java 17 で書かれ、Kotlin との相互運用を第一級で考慮した設計です（JSpecify による null 注釈付き API、イミュータブルな record、ビルダー）。

📚 **完全な API ドキュメント: [docs.finlight.me](https://docs.finlight.me)**

## 主な機能

- **REST API**: 記事の検索、リンクによる単一記事の取得、ニュースソースの一覧取得
- **リアルタイムストリーミング**: WebSocket 経由の enhanced および raw 記事ストリーム
- **標準で堅牢**: 指数バックオフによるリクエストのリトライ、WebSocket の自動再接続、pong ウォッチドッグ付きキープアライブ、先回りのコネクションローテーション、レート制限のハンドリング
- **Webhook 対応**: HMAC-SHA256 の署名検証とリプレイ攻撃対策
- **依存関係は最小限**: JSON に Jackson、ロギングに SLF4J（バインディングは利用側で用意）、HTTP/WebSocket は JDK 組み込みのクライアント

Java 17 以降が必要です。

## インストール

Gradle（Kotlin DSL）:

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

あるいは [JitPack](https://jitpack.io/#callbk/finlight-client-java) 経由で、GitHub のタグから直接ビルドすることもできます:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.callbk:finlight-client-java:v0.1.0")
}
```

## クイックスタート

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

### 記事を検索する

`query` の完全な構文は[クエリ言語リファレンス](https://docs.finlight.me)を参照してください。

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

### リンクから単一の記事を取得する

```java
Article article = client.articles().fetchArticleByLink(
    GetArticleByLinkParams.builder("https://www.reuters.com/technology/example")
        .includeContent(true)
        .build());
```

### ニュースソースを一覧取得する

```java
List<Source> sources = client.sources().getSources();
```

## リアルタイムストリーミング

`connect` はブロックし、`stop()` を呼ぶまで自動的に再接続します。バックグラウンドスレッドで動かす場合は `connectAsync` を使用してください。enhanced ストリームはセンチメントとエンティティ付きの記事を、raw ストリーム（`client.rawWebSocket()`）は最小遅延で未加工の記事を配信します。

```java
FinlightClient client = FinlightClient.create(apiKey);

client.webSocket().connect(
    GetArticlesWebSocketParams.builder()
        .tickers(List.of("AAPL", "NVDA"))
        .includeContent(true)
        .build(),
    article -> System.out.println(article.title()));
```

ストリームオプションのカスタマイズ:

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.of(apiKey),
    WebSocketOptions.builder()
        .takeover(true) // 同じキーの既存コネクションをテイクオーバーする
        .onClose((code, reason) -> System.out.println("closed: " + code))
        .build());
```

サーバーが接続を恒久的に拒否した場合（クローズコード 1008）、`connect` は `FinlightBlockedException` を送出します。再接続しても解決しないため、サポートにご連絡ください。

## Webhook

生の（パースされていない）リクエストボディで、受信した Webhook を検証します:

```java
import me.finlight.client.WebhookService;
import me.finlight.client.WebhookVerificationException;

// 例: Spring の controller で
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

## 設定

| オプション   | デフォルト                | 説明                                            |
| ------------ | ------------------------- | ----------------------------------------------- |
| `baseUrl`    | `https://api.finlight.me` | REST のエンドポイント                           |
| `wssUrl`     | `wss://wss.finlight.me`   | WebSocket のエンドポイント                      |
| `timeout`    | 5s                        | リクエストごとおよび接続のタイムアウト          |
| `retryCount` | 3                         | リトライ対象の失敗（429/5xx）に対する総試行回数 |
| `httpClient` | 組み込み                  | 独自の `java.net.http.HttpClient`（プロキシ等） |

```java
FinlightClient client = FinlightClient.create(
    FinlightConfig.builder(apiKey)
        .timeout(Duration.ofSeconds(10))
        .retryCount(5)
        .build());
```

ロギングは [SLF4J](https://www.slf4j.org/) を経由します。クライアントのログを見るには、バインディング（Logback、`slf4j-simple` など）を追加してください。ロガーの名前空間は `me.finlight.client` です。

## 開発

```bash
./gradlew build           # コンパイル、静的解析（Spotless + Error Prone）、テスト
./gradlew spotlessApply   # フォーマットの修正
```

### 実 API に対する検証

`local/` ディレクトリは gitignore されており、スモークおよびソークの実行スクリプトを収めています（Go や .NET のクライアントと同じ構成です）。認証情報は環境変数に置き、コードには決して書かないでください。

```bash
# 一度きりのスモーク: REST エンドポイント + 30 秒の記事ストリーム
FINLIGHT_API_KEY=sk_... ./gradlew -q smoke

# 長時間のストリーム安定性テスト（デフォルト 10 分）
FINLIGHT_API_KEY=sk_... SOAK_MINUTES=60 ./gradlew -q soak

# REST 統合テスト（キー未設定時は自動的にスキップ）
FINLIGHT_API_KEY=sk_... ./gradlew test --tests '*IntegrationSmokeTest'
```

任意: dev 環境を対象にする場合は `FINLIGHT_BASE_URL` / `FINLIGHT_WSS_URL` を指定します。

## ライセンス

[MIT](LICENSE)

## 関連リンク

- 日本語の製品ページ: https://finlight.me/ja/news-api
