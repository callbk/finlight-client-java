# finlight client for Java and Kotlin

*English | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | [한국어](README.ko.md)*

> **Breaking release.** Version 1.0.0 speaks only the finlight API v3. Code written for 0.1.x does not
> compile against 1.x. To stay on API v2, keep `me.finlight:finlight-client:0.1.0`; 0.1.x gets no further
> releases. See [Migrating from 0.1.x](#migrating-from-01x).

JVM client for the [finlight](https://finlight.me) news API: search and look up financial news,
companies and sources over REST, stream articles in real time over WebSocket, and verify webhooks.
Written in Java 17; Kotlin uses the same API (JSpecify null markers, builders, sealed types).

## Versions

| SDK version | API version | Status |
|---|---|---|
| 1.x | v3 (REST `/v3`, WebSocket `payloadVersion` 3, v3 webhooks) | current |
| 0.1.x | v2 | no further releases |

SDK and API versions are independent: new fields and values on the server come as compatible 1.x
releases. Other finlight SDKs that speak API v3: TypeScript 4.x, Python 3.x, PHP 2.x, Go module `/v2`,
.NET 2.x, Java 1.x, Ruby 1.x, Rust 0.2.

## Requirements

- Java 17 or newer (tested on 17 and 21)
- Jackson `jackson-databind` 2.13.5 or newer (tested with 2.13.5 and 2.18.3). The published POM names
  2.13.5 as the floor and imports no Jackson BOM, so your build keeps its own Jackson version.
- JSpecify 1.0 (annotations only)

REST and WebSocket use the JDK's `java.net.http` client; there is no other runtime dependency.

## Installation

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("me.finlight:finlight-client:1.0.0")
}
```

Maven:

```xml
<dependency>
  <groupId>me.finlight</groupId>
  <artifactId>finlight-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Quick start

```java
import me.finlight.client.*;

try (FinlightClient client = new FinlightClient(System.getenv("FINLIGHT_API_KEY"))) {
  ArticlePage page = client.articles().search(
      SearchParams.builder().query("ticker:NVDA").limit(10).build());
  for (Article a : page.articles()) {
    System.out.println(a.publishedAt() + " " + a.title());
  }
}
```

Kotlin:

```kotlin
FinlightClient(System.getenv("FINLIGHT_API_KEY")).use { client ->
    val page = client.articles().search(SearchParams.builder().query("ticker:NVDA").limit(10).build())
    page.articles().forEach { println("${it.publishedAt()} ${it.title()}") }
}
```

REST calls block the calling thread. Every builder method carries the name of one API key; only the
keys you set are sent.

## Search articles

```java
SearchParams params = SearchParams.builder()
    .query("eventType:mna AND companyCountry:US")
    .languages("en", "de")
    .from(LocalDate.of(2026, 11, 1))          // or a String, Instant, OffsetDateTime
    .eventTypes(EventType.MNA)
    .minRelevance(0.5)
    .sourceTypes(SourceType.NEWSWIRE, SourceType.FINANCIAL_MEDIA)
    .orderBy(OrderBy.AVAILABLE_AT)
    .include(IncludeBlock.CONTENT, IncludeBlock.COMPANIES, IncludeBlock.EVENTS)
    .limit(50)
    .build();
ArticlePage page = client.articles().search(params);
System.out.println(page.pagination().hasMore() + " " + page.pagination().nextCursor());
```

`sourceTypes` and `excludeSourceTypes` filter by the type of the source (`SourceType`: `newswire`,
`press_release`, `financial_media`, `general_media`, `aggregator`, `blog`, `government`). Articles of
an unclassified source never match `sourceTypes` and are kept by `excludeSourceTypes`. Both filter
inside your source pool and never add a source to it. `source().type()` and `Source.type()` are null
for an unclassified source.

`searchAll` walks the pages for you. It freezes the request body when you call it and only adds the
cursor on later pages. It stops after `maxPages` pages (default 10), because every page costs quota.

```java
SearchAllResult all = client.articles().searchAll(params, SearchAllOptions.maxPages(5));
for (Article a : all) {                     // or all.pages(), or all.stream()
  System.out.println(a.id() + " " + a.title());
}
if (all.truncated()) {
  SearchParams next = params.withCursor(all.nextCursor());   // continue later
}
```

A result can be iterated once. Wrong shapes (for example `limit(0)` or an empty `languages`) throw
`FinlightValidationException` before anything is sent; an unknown key does not compile. Vocabulary
values are not checked locally, so new server values work without an SDK update. Dates go out as UTC
with milliseconds (`2026-11-01T00:00:00.000Z`), plain dates as `YYYY-MM-DD`, strings unchanged.

## Look up articles, companies and sources

```java
Article a = client.articles().get("EU8JwpsZ8iJehuyPZjMqRz", LookupOptions.include(IncludeBlock.CONTENT));
Article b = client.articles().getByLink("https://www.reuters.com/...");

CompanyList found = client.companies().search(CompanySearchParams.of("NVDA", "XNAS")); // at most 10
CompanyList many = client.companies().getMany(4821L, 1001L);
CompanyRecord nvda = client.companies().get(4821);       // revalidated with its ETag next time
String etag = nvda.cacheInfo().etag();
CompanyRecord changed = client.companies().get(4821, CompanyGetOptions.ifNoneMatch(etag)); // null: not modified

SourceList sources = client.sources().list();
```

- A miss throws `NotFoundException` (a 404 is an error, not an empty result). Articles indexed before
  ids existed are found by link only.
- Company lookups need the `entityAccess` entitlement (`ForbiddenException` otherwise).
- Every company and source lookup costs 1 credit of your monthly quota per successful request
  (`getMany` with several ids too). A 304 revalidation and errors are free.
- `companies().get(id)` keeps the record with its ETag and revalidates it; an unchanged record comes
  from the cache with `cacheInfo().notModified()` true. With your own `ifNoneMatch` a 304 returns null.
- Lists (`CompanyList`, `SourceList`, `ArticlePage.articles()`) skip an entry that cannot be read, log a
  warning and keep it in `skipped()` with its index and raw JSON.

## Absent, null and empty

Keys that depend on your plan or on `include` have three states, held by `Gated<T>`:

| State | Meaning | `Gated` |
|---|---|---|
| absent | not requested, not entitled, or a raw article | `Gated.Absent` |
| null | requested and entitled, but no value | `Gated.Present` with `value() == null` |
| value | the value | `Gated.Present` |

```java
if (a.contentPresent()) {               // the key was sent, maybe as null
  String text = a.content().value();    // null means "no text for this article"
}
if (a.companiesPresent()) {
  List<Company> cs = a.companies().value();   // [] means "tagged, no company found"
}
switch (a.sentiment()) {                // Java 21 pattern matching; Kotlin: when (val s = a.sentiment())
  case Gated.Present<Sentiment> p -> System.out.println(p.value());
  case Gated.Absent<Sentiment> x -> System.out.println("not in your plan");
}
```

`content` and `sentiment` can be absent, null or a value. `companies`, `events`, `events[].companyIds`
and `Source.hasContent` are absent or a value (`[]` is a value). `companyIds` is only sent when
`companies` is in the same payload. Absent is never turned into `[]`, `false` or `0`.

Timestamps stay the strings that were sent (`publishedAt()`); `publishedAtTime()` and the other
`*Time()` methods parse them strictly (RFC 3339) and return null for anything unreadable. Unknown keys
from newer servers are ignored; `rawJson()` on `Article`, `CompanyRecord`, `Source`, `StreamEvent`,
`WebhookEvent` and the API errors returns the JSON as received. Vocabulary values are open: compare
with the constants (`CompanyRole.ACQUIRER`, `EventTypeValues.MNA` in a `switch`); unknown values are
kept as sent.

## Real-time streams

```java
StreamParams params = StreamParams.builder()
    .query("ticker:NVDA")
    .language("en")
    .include(IncludeBlock.COMPANIES, IncludeBlock.EVENTS)
    .sourceTypes(SourceType.NEWSWIRE, SourceType.PRESS_RELEASE)
    .includeUpdates(true)
    .build();
FinlightStream stream = client.stream(params, new StreamListener() {
  @Override public void onEvent(StreamEvent e) {
    if (e instanceof StreamEvent.Created c) System.out.println("new: " + c.data().title());
    else if (e instanceof StreamEvent.Updated u) System.out.println("updated: " + u.data().title());
    // StreamEvent.Other: an event this SDK version does not know
  }
  @Override public void onReconnect(ReconnectInfo info) {
    System.out.println("reconnected (" + info.reason() + "), last seen " + info.lastSeenAt());
  }
  @Override public void onError(FinlightException error) { System.err.println(error.getMessage()); }
  @Override public void onClosed(FinlightException terminal) { /* null after close() */ }
});
// ... later
stream.close();
```

Kotlin can pass a lambda for `onEvent`: `client.stream(params) { e -> println(e.event()) }`.

- `client.rawStream(RawStreamParams, listener)` delivers articles before enrichment (at
  `<wssUrl>/raw`): no `include`, no content, sentiment, companies or events, `availableAt` always null.
- `sourceTypes` and `excludeSourceTypes` work on both streams as in search. The SDK sends them only
  when you set them. A value the server does not know ends the stream with
  `ValidationFailedException` (field `sourceTypes`, with the allowed values in `errors()`).
- Listener methods run on one daemon thread of the stream, in order. A slow listener does not trip
  the keep-alive; an exception in it is logged and the stream goes on. After `close()` no more events
  arrive; `onClosed` runs last. `stream.awaitTermination()` blocks until the end.
- The stream pings every 25 s, replaces a connection without pong for 60 s, renews the connection
  after 115 minutes and sends the same subscribe after every reconnect. Duplicates are dropped;
  `article.created` and `article.updated` for one article are two events.
- It never backfills. `onReconnect` gets the latest `availableAt` (raw: `firstSeenAt`) you saw. Fill
  gaps with `searchAll` from that time with `orderBy` `availableAt` (raw: `firstSeenAt`).
- Terminal problems end the stream through `onClosed(terminal)`: an invalid key
  (`UnauthorizedException`), no WebSocket access (`ForbiddenException`), a rejected subscribe
  (`ValidationFailedException`), and `PreemptedException` with reason `connection_limit` when another
  connection took over. Other problems reconnect and go to `onError`.
- When the monthly WebSocket quota is used up (the dial is refused with "quota exceeded", or
  `PreemptedException` with reason `quota_exceeded`), the stream does not end: it tries again every
  hour until the quota allows it. Each wait logs a warning and calls `onQuotaWait`. Set
  `quotaReconnect(false)` to end the stream with the error instead; `quotaReconnectInterval` changes
  the hour.

Limits to know:

- Your plan's connection limit is shared by enriched, raw and older SDK connections. A dial at the
  limit is refused; the SDK retries after 60 s. `StreamOptions.takeover(true)` replaces your stalest
  connection instead.
- Right after each subscribe, the server sends the newest matching article once. It can be old.
- The gateway ends every connection after 2 hours; the SDK renews it before that.
- Live frames over 32,000 bytes are dropped by the server without notice. This can happen with
  `include` content on long articles. Fetch them with `articles().get(id)`.
- `connection.admitted` (`onAdmitted`) arrives before the server checks the subscribe. It does not
  mean the subscribe was accepted.

## Webhooks

finlight signs every webhook. Verify it with the **raw** request body, before any framework parses it:

```java
WebhookPayload payload;
try {
  payload = Webhooks.constructEvent(rawBodyBytes, headers, System.getenv("FINLIGHT_WEBHOOK_SECRET"));
} catch (WebhookVerificationException e) {
  return 400;                                   // never 410
}
if (payload instanceof WrongPayloadVersion old) {
  keepForLater(old.raw());                      // an old v2 body: answer 2xx anyway
  return 204;
}
WebhookEvent event = (WebhookEvent) payload;
if (alreadySeen(event.eventId())) return 204;   // deliveries can repeat
queue(event);                                   // answer within 5 s, work afterwards
return 204;
```

- `headers` is any `Map<String, ?>` with string, collection or array values (Servlet, Spring, JDK
  `HttpExchange.getRequestHeaders()`). Names match without regard to case. The overload
  `constructEvent(body, signature, timestamp, secret)` takes the two header values;
  `verifySignature` only checks.
- The timestamp must be within 300 s (`WebhookVerifyOptions.tolerance`) in both directions.
- `event.isTest()` is true for the dashboard test webhook. It is not cut by your plan.
- Never answer with 410: it disables the webhook at once. Ten non-2xx answers in a row disable it too.

Getting the raw body:

| Framework | Raw body |
|---|---|
| JDK `HttpServer` | `exchange.getRequestBody().readAllBytes()` |
| Servlet | `request.getInputStream().readAllBytes()` |
| Spring MVC | `@RequestBody byte[] body` |
| Ktor | `call.receive<ByteArray>()` |

Switching a webhook to v3: deploy this SDK first; answer `WrongPayloadVersion` with 2xx and keep its
`raw()`; then switch the webhook to `payloadVersion` 3 in the dashboard; then wait about 2 hours,
because retries queued before the switch still arrive in the old format.

## Errors and retries

Every error extends `FinlightException` (a `RuntimeException`) and has `kind()`:

```text
FinlightException                kind: api, validation, network, timeout, decode, webhook_verification, preempted
├── FinlightApiException         status, code, type, title, detail, requestId, errors, apiKind, raw
│   ├── ValidationFailedException
│   ├── CursorInvalidException
│   ├── CursorMismatchException
│   ├── UnauthorizedException
│   ├── ForbiddenException
│   ├── NotFoundException
│   ├── RateLimitedException     limitType (monthly, burst, unknown or newer), retryAfter
│   └── InternalServerException
├── FinlightValidationException  param; thrown before any request
├── FinlightNetworkException     the transport error is getCause()
│   └── FinlightTimeoutException
├── FinlightDecodeException      path, raw
├── WebhookVerificationException reason
└── PreemptedException           reason, newLeaseId, timestamp
```

- 5xx answers, network errors and attempt timeouts are retried up to 3 times with growing, jittered
  waits, the POST search included.
- 400, 401, 403 and 404 are never retried. A monthly 429 fails at once, also inside `searchAll`: REST
  never waits for a new quota period (only streams do).
- A burst 429 waits exactly `Retry-After` (10 s without the header) and tries once more. This is on by
  default and does not count against `maxRetries`; `retryOnBurst(RetryOnBurst.OFF)` throws at once.
- Every call sends its own `X-Request-Id`, the same on each retry; `requestId()` carries it.
- The JDK `HttpClient` itself can repeat a GET when it could not connect or a pooled connection
  turned out to be closed. Such a repeat keeps the `X-Request-Id` and does not count against
  `maxRetries`. `-Djdk.httpclient.disableRetryConnect=true` turns off the repeat after a failed
  connect for the whole JVM; the repeat on a closed pooled connection cannot be turned off.
- Interrupt the calling thread to cancel a call: waits end at once, nothing is retried, and the call
  throws `java.util.concurrent.CancellationException` with the interrupt flag restored.

## Configuration

```java
FinlightConfig config = FinlightConfig.builder(apiKey)
    .baseUrl("https://api.finlight.me")       // a trailing slash is fine
    .wssUrl("wss://wss.finlight.me")          // raw streams use <wssUrl>/raw
    .timeout(Duration.ofSeconds(30))          // per attempt
    .totalTimeout(Duration.ofMinutes(2))      // per call, retries included; default none
    .retryOnBurst(RetryOnBurst.ONCE)
    .quotaReconnect(true)
    .build();
FinlightClient client = new FinlightClient(config);
```

| Key | Default |
|---|---|
| `apiKey` | required, not empty |
| `baseUrl` | `https://api.finlight.me` |
| `wssUrl` | `wss://wss.finlight.me` (raw: `/raw`) |
| `timeout` | 30 s per attempt |
| `totalTimeout` | none |
| `maxRetries` | 3 |
| `retryBaseDelay`, `retryMaxDelay` | 0.5 s, 8 s |
| `retryOnBurst` | `RetryOnBurst.ONCE`; `RetryOnBurst.OFF` throws at once |
| `burstWait` | 10 s, only without `Retry-After` |
| `maxPages` | 10 |
| `maxResponseBytes` | 32 MiB |
| `companyCacheSize` | 256 (0 turns it off) |
| `pingInterval` | 25 s |
| `watchdogTimeout` | 60 s |
| `rotationInterval` | 115 min |
| `reconnectBaseDelay`, `reconnectMaxDelay` | 1 s, 30 s |
| `longBackoff` | 60 s |
| `quotaReconnect`, `quotaReconnectInterval` | true, 1 h |
| `searchFailedThreshold` | 5 |
| `dedupeCacheSize` | 1000 |
| `maxMessageBytes` | 32 MiB (at least 16 MiB) |
| `connectTimeout` | 30 s |
| `clock` | the system clock |
| `httpClient` | one the SDK creates |

`httpClient(java.net.http.HttpClient)` uses your client for REST and WebSocket (proxy, TLS, executor).
Its connect timeout and proxy settings apply; the SDK never closes it. Do not wrap it with your own
retries: every attempt costs quota. The JDK's own GET repeats (see Errors and retries) apply to it
too. `client.close()` ends all streams of the client and shuts down the
HTTP client the SDK created.

## Logging

The SDK logs through the platform logger `System.getLogger("finlight")` (java.util.logging unless you
installed another backend, for example `slf4j-jdk-platform-logging`). Details go to DEBUG, problems to
WARNING; nothing else is logged by default, and the SDK never writes to stdout or stderr itself. API
keys, webhook secrets, signatures and bodies are never logged or put into error messages, and
`FinlightConfig.toString()` hides the key.

## Data notes

- Old articles can carry `publishedAt` `"1970-01-01T00:00:00.000Z"`. It is delivered as sent.
- Empty texts come as null (`summary`, `content`), never as `""`.
- Articles tagged before roles existed have `role` and `relevance` null and `events` `[]`.
- `articles().get(id)` answers 404 for articles indexed before ids existed. Use `getByLink`.
- `availableAt` is null on older articles and always null on raw streams.

## Migrating from 0.1.x

The full guide is on [docs.finlight.me](https://docs.finlight.me). The short version:

| 0.1.x | 1.x |
|---|---|
| `FinlightClient.create(key)` | `new FinlightClient(key)` or `new FinlightClient(FinlightConfig.builder(key).build())` |
| `articles().fetchArticles(GetArticlesParams)` | `articles().search(SearchParams)`, `articles().searchAll(...)` |
| `page` / `pageSize` | `limit` / `cursor` |
| `includeContent`, `includeEntities` | `include(IncludeBlock.CONTENT, IncludeBlock.COMPANIES, IncludeBlock.EVENTS)` |
| `OrderBy.PUBLISH_DATE`, `SortOrder.DESC` | `OrderBy.PUBLISHED_AT`, `SortOrder.DESC` (lowercase on the wire) |
| `publishDate`, `createdAt` | `publishedAt`, `firstSeenAt` |
| `images` | `media()` with `url()` |
| `source` (string) | `source().domain()` |
| `sentiment`, `confidence` | `sentiment()` (`Gated<Sentiment>`) with `label()` and `confidence()` |
| `fetchArticleByLink(GetArticleByLinkParams)` | `articles().getByLink(link, LookupOptions)` |
| `sources().getSources()` | `sources().list()` |
| `webSocket().connect(params, consumer)` | `client.stream(StreamParams, StreamListener)` |
| `rawWebSocket()` | `client.rawStream(RawStreamParams, StreamListener)` |
| `FinlightBlockedException` | `onClosed(terminal)` with a typed `FinlightException` |
| webhook `isUpdate` | `event.event()` equals `WebhookEventName.ARTICLE_UPDATED` |
| SLF4J logger | `System.Logger` named `finlight` |

0.1.x and 1.x use the same package and do not work side by side on one classpath. Do not run 0.1.x and
1.x streams side by side on one key either: they share the connection limit.

Switching a webhook: deploy 1.x, answer `WrongPayloadVersion` with 2xx and keep `raw()`, switch the
webhook to `payloadVersion` 3 in the dashboard, then wait about 2 hours for queued retries.

## Development

```bash
./gradlew build                      # compile, unit and conformance tests (no network, no keys), examples
./gradlew checkConformance           # the vendored kit matches its MANIFEST (sha256)
./gradlew syncConformance -PkitDir=<monorepo>/shared/contracts/api-v3
./gradlew test -PjacksonVersion=2.13.5   # with the lowest supported Jackson
```

The contract kit (`conformance/`, version in `conformance/CONTRACT_VERSION`) is vendored and checked
against its sha256 `MANIFEST`. `CONFORMANCE.md` maps every kit case to its test. The five examples in
`examples/` are compiled and run against local mocks by the test suite. Live checks run only against
dev, never as part of `./gradlew test`, and need `FINLIGHT_API_KEY`, `FINLIGHT_BASE_URL` and
`FINLIGHT_WSS_URL`: `./gradlew liveTest`.

## License

MIT
