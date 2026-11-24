# Changelog

## 1.0.0 (unreleased)

**Breaking: 1.x speaks finlight API v3 only.** REST `/v3`, WebSocket `payloadVersion` 3 and v3 webhooks.
Code written for 0.1.x does not compile against 1.x, and both cannot share one classpath. To stay on
API v2, keep `me.finlight:finlight-client:0.1.0`; 0.1.x gets no further releases. See the migration
section of the README and the full guide on [docs.finlight.me](https://docs.finlight.me).

### Breaking

- The client is built with `new FinlightClient(key)` or `new FinlightClient(FinlightConfig)` (was
  `FinlightClient.create`). Methods: `articles().search`, `articles().searchAll`, `articles().get`,
  `articles().getByLink`, `companies().search`, `companies().getMany`, `companies().get`,
  `sources().list`, `stream`, `rawStream`, `Webhooks.constructEvent`, `Webhooks.verifySignature`.
- Models follow the v3 wire format: `publishedAt`, `firstSeenAt`, `revisedAt`, `availableAt`,
  `media[].url`, `source.domain`, `sentiment { label, confidence }`, `companies[]` with `role` and
  `relevance`, and `events[]`. Models are final classes with accessors (no public records).
- Keys that depend on the plan or `include` (`content`, `sentiment`, `companies`, `events`,
  `events[].companyIds`, `Source.hasContent`) are `Gated<T>`: absent, null or a value. They are never
  turned into null, `[]` or `false`. Use `contentPresent()`, `sentimentPresent()`, `companiesPresent()`
  and `eventsPresent()`.
- Search takes `limit` and `cursor` instead of pages, `languages`, lowercase `order`, and
  `include(content, companies, events)`. Only the keys you set are sent.
- Response vocabularies are open value classes (`CompanyRole`, `EventType`, ...) with constants and
  `...Values` string constants for `switch`; the closed enums `Category`, `OrderBy` and `SortOrder` of
  0.1.x are gone.
- Streams take a `StreamListener` and return a `FinlightStream` handle (`close()`,
  `awaitTermination()`); terminal errors arrive in `onClosed(terminal)`.
- Webhooks: `constructEvent(rawBody, headers, secret)` returns a `WebhookPayload`: a `WebhookEvent`
  (`eventId`, `event`, `occurredAt`, `changedFields`, `data`, `isTest()`) or, for a correctly signed
  body in the old format, a `WrongPayloadVersion` result (answer it with 2xx). The timestamp is
  required and must be within 300 s.
- Logging moved from SLF4J to `System.Logger` named `finlight`. The SLF4J dependency is gone.

### Added

- `searchAll` with `maxPages` (default 10), page and `Stream` views, `nextCursor()` and `truncated()`.
- Company lookups with ETag revalidation (`cacheInfo()`) and the source catalog.
- One error model: `FinlightApiException` with `status`, `code`, `type`, `title`, `detail`,
  `requestId`, `errors`, `apiKind`, `raw` and one subclass per code (`NotFoundException`,
  `RateLimitedException` with `limitType` and `retryAfter`, ...), plus `FinlightValidationException`,
  `FinlightNetworkException`, `FinlightTimeoutException`, `FinlightDecodeException`,
  `WebhookVerificationException` and `PreemptedException`. All share `FinlightException` and `kind()`.
- Retries: 5xx, network errors and attempt timeouts up to 3 times with jittered backoff (POST search
  included); 400, 401, 403, 404 and a monthly 429 never (also not in `searchAll`). A burst 429 waits
  `Retry-After` (10 s when missing) and retries once; on by default, `retryOnBurst(false)` turns it off.
  One `X-Request-Id` per call, kept on retries. Interrupting the thread cancels a call at once.
- Streams: application ping every 25 s, a 60 s pong watchdog, connection renewal after 115 min,
  duplicate filter, hooks `onAdmitted`, `onReconnect` (with `lastSeenAt`), `onError`, `onQuotaWait`.
- Streams survive a used-up WebSocket quota: after a dial refused with "quota exceeded" or a
  `quota_exceeded` preemption they retry every hour until the quota allows it, reporting each wait
  to `onQuotaWait`. On by default; `quotaReconnect(false)` ends the stream with the error instead.
  A `connection_limit` preemption ends the stream.
- `rawJson()` on articles, records, sources, stream events, webhook events and API errors.
- `User-Agent: finlight-client-java/<version>` and `x-client-version` on every request and dial;
  the WebSocket key header is sent as lowercase `x-api-key`.

### Removed

- Everything for API v1 and v2: the old services, records, parameter types and transforms.

### Contract

- Built and tested against the finlight API v3 contract kit 3.0.0-rc.3, vendored in `conformance/`
  (not published). Every kit case runs as its own test (`CONFORMANCE.md`).
