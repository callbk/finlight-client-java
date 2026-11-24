# Conformance

This client runs every case of the finlight API v3 contract kit `3.0.0-rc.4` (vendored in
`conformance/`) as its own JUnit 5 dynamic test. The test name starts with the case id.
HTTP cases run against a real HTTP server on 127.0.0.1 (`HttpMock`), WebSocket cases against an
in-process WebSocket server on raw sockets (`WsServer`), with a fake clock (`FakeClock`).
`ConformanceCoverageTest` fails when a kit case has no test or this table misses one.

Check the kit against its MANIFEST with `./gradlew checkConformance`; update it with
`./gradlew syncConformance -PkitDir=<monorepo>/shared/contracts/api-v3`.

| Case | Title | Fixture | Test |
|---|---|---|---|
| V-01 | Known constants equal vocab.json | `vocab.json` | `ConformanceVocabularyTest` > `V-01 ...` |
| D-01 | Full article: every include, every entitlement | `fixtures/decode/full-article.json` | `ConformanceDecodeTest` > `D-01 ...` |
| D-02 | Raw skeleton (raw channel ignores include and entitlements) | `fixtures/decode/raw-skeleton.json` | `ConformanceDecodeTest` > `D-02 ...` |
| D-03 | No include, every entitlement | `fixtures/decode/no-include-full-access.json` | `ConformanceDecodeTest` > `D-03 ...` |
| D-04 | Every include requested, no entitlement | `fixtures/decode/all-include-no-access.json` | `ConformanceDecodeTest` > `D-04 ...` |
| D-05 | events requested and entitled, companies not requested | `fixtures/decode/events-without-companies.json` | `ConformanceDecodeTest` > `D-05 ...` |
| D-06 | events and companies requested, entityAccess missing | `fixtures/decode/events-companies-not-entitled.json` | `ConformanceDecodeTest` > `D-06 ...` |
| D-07 | Legacy values delivered as they are | `fixtures/decode/legacy-values.json` | `ConformanceDecodeTest` > `D-07 ...` |
| D-08 | Non-ASCII text | `fixtures/decode/non-ascii.json` | `ConformanceDecodeTest` > `D-08 ...` |
| D-09 | Sentiment with a null label, event with null direction/importance/confidence, companies [] | `fixtures/decode/partial-values.json` | `ConformanceDecodeTest` > `D-09 ...` |
| D-10 | Decode matrix (include x entitlements x enriched/raw) | `fixtures/decode/matrix.json` | `ConformanceDecodeTest` > `D-10 ...` |
| D-11 | Blank summary and content arrive as null | `fixtures/decode/blank-text.json` | `ConformanceDecodeTest` > `D-11 ...` |
| F-01 | Unknown fields at every level of the article | `fixtures/forward-compat/unknown-fields.json` | `ConformanceDecodeTest` > `F-01 ...` |
| F-02 | Unknown values in every open response enum | `fixtures/forward-compat/unknown-enum-values.json` | `ConformanceDecodeTest` > `F-02 ...` |
| F-03 | New keys on REST, webhook and WebSocket envelopes | `fixtures/forward-compat/envelope-keys.json` | `ConformanceDecodeTest` > `F-03 ...` |
| F-04 | Unknown webhook event name, unknown problem code, unknown 429 limitType | `fixtures/forward-compat/unknown-events-and-codes.json` | `ConformanceDecodeTest` > `F-04 ...` |
| R-01 | articles.search with one parameter sends exactly that parameter | `fixtures/requests/search-minimal.json` | `ConformanceRestTest` > `R-01 ...` |
| R-02 | articles.search with every parameter | `fixtures/requests/search-all-parameters.json` | `ConformanceRestTest` > `R-02 ...` |
| R-03 | articles.search without parameters | `fixtures/requests/search-no-parameters.json` | `ConformanceRestTest` > `R-03 ...` |
| R-04 | articles.get with include, comma-joined | `fixtures/requests/get-by-id.json` | `ConformanceRestTest` > `R-04 ...` |
| R-05 | articles.getByLink URL-encodes the link | `fixtures/requests/get-by-link.json` | `ConformanceRestTest` > `R-05 ...` |
| R-06 | companies.search with q and mic | `fixtures/requests/companies-search.json` | `ConformanceRestTest` > `R-06 ...` |
| R-07 | companies.getMany joins ids with commas | `fixtures/requests/companies-get-many.json` | `ConformanceRestTest` > `R-07 ...` |
| R-08 | companies.get returns ETag and Cache-Control | `fixtures/requests/companies-get.json` | `ConformanceRestTest` > `R-08 ...` |
| R-09 | companies.get with If-None-Match answers 304 | `fixtures/requests/companies-get-not-modified.json` | `ConformanceRestTest` > `R-09 ...` |
| R-10 | sources.list with fullArticleAccess | `fixtures/requests/sources-list-full.json` | `ConformanceRestTest` > `R-10 ...` |
| R-11 | sources.list without fullArticleAccess | `fixtures/requests/sources-list-basic.json` | `ConformanceRestTest` > `R-11 ...` |
| R-12 | An unknown request key is dropped silently by the server | `fixtures/requests/search-unknown-key.json` | `ConformanceRestTest` > `R-12 ...` |
| R-13 | WebSocket subscribe frame, enriched | `fixtures/requests/ws-subscribe-enriched.json` | `ConformanceSessionTest` > `R-13 ...` |
| R-14 | WebSocket subscribe frame, raw | `fixtures/requests/ws-subscribe-raw.json` | `ConformanceSessionTest` > `R-14 ...` |
| P-01 | Three-page cursor walk | `fixtures/pagination/three-pages.json` | `ConformanceRestTest` > `P-01 ...` |
| P-02 | limit and include may change between pages | `fixtures/pagination/resize-and-reproject.json` | `ConformanceRestTest` > `P-02 ...` |
| P-03 | Adding "to" on page 2 breaks the cursor | `fixtures/pagination/injected-to.json` | `ConformanceRestTest` > `P-03 ...` |
| P-04 | Adding the default languages ["en"] on page 2 breaks the cursor | `fixtures/pagination/injected-default-languages.json` | `ConformanceRestTest` > `P-04 ...` |
| P-05 | Changing order on page 2 | `fixtures/pagination/changed-order.json` | `ConformanceRestTest` > `P-05 ...` |
| P-06 | Reordering list values keeps the cursor valid | `fixtures/pagination/reordered-list.json` | `ConformanceRestTest` > `P-06 ...` |
| E-01 | Unknown enum value | `fixtures/errors/validation-enum.json` | `ConformanceRestTest` > `E-01 ...` |
| E-02 | Several invalid fields at once | `fixtures/errors/validation-multiple.json` | `ConformanceRestTest` > `E-02 ...` |
| E-03 | Unknown field in the query language | `fixtures/errors/validation-query-language.json` | `ConformanceRestTest` > `E-03 ...` |
| E-04 | Empty languages array | `fixtures/errors/validation-empty-languages.json` | `ConformanceRestTest` > `E-04 ...` |
| E-05 | Unknown include value on a GET lookup | `fixtures/errors/validation-include-query.json` | `ConformanceRestTest` > `E-05 ...` |
| E-06 | companies.search without q and ids | `fixtures/errors/companies-neither.json` | `ConformanceRestTest` > `E-06 ...` |
| E-07 | companies.search with q and ids | `fixtures/errors/companies-both.json` | `ConformanceRestTest` > `E-07 ...` |
| E-08 | Non-integer company id in ids | `fixtures/errors/companies-ids-not-integer.json` | `ConformanceRestTest` > `E-08 ...` |
| E-09 | Non-integer companyId in the path | `fixtures/errors/company-id-not-integer.json` | `ConformanceRestTest` > `E-09 ...` |
| E-10 | Cursor that is not a cursor | `fixtures/errors/cursor-invalid.json` | `ConformanceRestTest` > `E-10 ...` |
| E-11 | Cursor of an unsupported version | `fixtures/errors/cursor-version.json` | `ConformanceRestTest` > `E-11 ...` |
| E-12 | No API key | `fixtures/errors/unauthorized-missing-key.json` | `ConformanceRestTest` > `E-12 ...` |
| E-13 | Unknown API key | `fixtures/errors/unauthorized-invalid-key.json` | `ConformanceRestTest` > `E-13 ...` |
| E-14 | Company lookup without entityAccess | `fixtures/errors/forbidden-companies.json` | `ConformanceRestTest` > `E-14 ...` |
| E-15 | Unknown article id | `fixtures/errors/not-found-id.json` | `ConformanceRestTest` > `E-15 ...` |
| E-16 | Unknown article link | `fixtures/errors/not-found-link.json` | `ConformanceRestTest` > `E-16 ...` |
| E-17 | Unknown company id | `fixtures/errors/not-found-company.json` | `ConformanceRestTest` > `E-17 ...` |
| E-18 | Monthly token limit reached | `fixtures/errors/rate-limited-monthly.json` | `ConformanceRestTest` > `E-18 ...` |
| E-19 | Burst limit reached | `fixtures/errors/rate-limited-burst.json` | `ConformanceRestTest` > `E-19 ...` |
| E-20 | Server failure | `fixtures/errors/internal-error.json` | `ConformanceRestTest` > `E-20 ...` |
| E-21 | Malformed JSON body (NestJS fallback form) | `fixtures/errors/malformed-json-body.json` | `ConformanceRestTest` > `E-21 ...` |
| E-22 | Unknown route under /v3 (NestJS fallback form) | `fixtures/errors/unknown-route.json` | `ConformanceRestTest` > `E-22 ...` |
| E-23 | No X-Request-Id sent: the server generates one | `fixtures/errors/generated-request-id.json` | `ConformanceRestTest` > `E-23 ...` |
| E-24 | Non-JSON error body | `fixtures/errors/gateway-non-json.json` | `ConformanceRestTest` > `E-24 ...` |
| E-25 | Cursor issued for another sort order | `fixtures/errors/cursor-mismatch.json` | `ConformanceRestTest` > `E-25 ...` |
| W-01 | article.created, enriched, every include, non-ASCII body | `fixtures/webhook/created-enriched.json` | `ConformanceWebhookTest` > `W-01 ...` |
| W-02 | article.updated with changedFields | `fixtures/webhook/updated-with-changed-fields.json` | `ConformanceWebhookTest` > `W-02 ...` |
| W-03 | article.updated for a plan without fullArticleAccess | `fixtures/webhook/updated-content-not-entitled.json` | `ConformanceWebhookTest` > `W-03 ...` |
| W-04 | article.created on a raw webhook | `fixtures/webhook/created-raw.json` | `ConformanceWebhookTest` > `W-04 ...` |
| W-05 | Dashboard test webhook (evt_test…) | `fixtures/webhook/test-webhook.json` | `ConformanceWebhookTest` > `W-05 ...` |
| W-06 | v2 body is reported as wrong payload version | `fixtures/webhook/v2-body.json` | `ConformanceWebhookTest` > `W-06 ...` |
| W-07 | Body changed after signing | `fixtures/webhook/neg-tampered-body.json` | `ConformanceWebhookTest` > `W-07 ...` |
| W-08 | Timestamp older than the tolerance | `fixtures/webhook/neg-stale-timestamp.json` | `ConformanceWebhookTest` > `W-08 ...` |
| W-09 | Timestamp further in the future than the tolerance | `fixtures/webhook/neg-future-timestamp.json` | `ConformanceWebhookTest` > `W-09 ...` |
| W-10 | Timestamp header is not ISO 8601 | `fixtures/webhook/neg-unparseable-timestamp.json` | `ConformanceWebhookTest` > `W-10 ...` |
| W-11 | Timestamp in unix seconds instead of ISO 8601 | `fixtures/webhook/neg-unix-seconds-timestamp.json` | `ConformanceWebhookTest` > `W-11 ...` |
| W-12 | No X-Webhook-Timestamp header | `fixtures/webhook/neg-missing-timestamp.json` | `ConformanceWebhookTest` > `W-12 ...` |
| W-13 | No X-Webhook-Signature header | `fixtures/webhook/neg-missing-signature.json` | `ConformanceWebhookTest` > `W-13 ...` |
| W-14 | Signature without the sha256= prefix | `fixtures/webhook/neg-wrong-prefix.json` | `ConformanceWebhookTest` > `W-14 ...` |
| W-15 | Signature computed with the hex-decoded secret | `fixtures/webhook/neg-secret-hex-decoded.json` | `ConformanceWebhookTest` > `W-15 ...` |
| W-16 | Case-insensitive header lookup | `fixtures/webhook/lowercase-headers.json` | `ConformanceWebhookTest` > `W-16 ...` |
| S-01 | Ping before subscribe gets a v2 pong; after the v3 subscribe pongs are v3 | `ws-sessions/v2-pong-before-subscribe.json` | `ConformanceSessionTest` > `S-01 ...` |
| S-02 | Lowercase x-api-key on the wire | `ws-sessions/v2-pong-before-subscribe.json` | `ConformanceSessionTest` > `S-02 ...` |
| S-03 | connection.admitted echoes the nonce; a re-subscribe re-admits only with the same nonce | `ws-sessions/admitted-nonce.json` | `ConformanceSessionTest` > `S-03 ...` |
| S-04 | article.created then article.updated for the same id, then an exact duplicate | `ws-sessions/created-then-updated.json` | `ConformanceSessionTest` > `S-04 ...` |
| S-05 | An unknown event name arrives | `ws-sessions/unknown-event.json` | `ConformanceSessionTest` > `S-05 ...` |
| S-06 | connection.error PING_ON_DEFAULT_ROUTE | `ws-sessions/ping-on-default-route.json` | `ConformanceSessionTest` > `S-06 ...` |
| S-07 | VALIDATION_FAILED for a v2 key in a v3 subscribe | `ws-sessions/validation-failed-forbidden-key.json` | `ConformanceSessionTest` > `S-07 ...` |
| S-08 | VALIDATION_FAILED for include on raw | `ws-sessions/raw-include-rejected.json` | `ConformanceSessionTest` > `S-08 ...` |
| S-09 | connection.preempted, reason connection_limit (another connection took over) | `ws-sessions/preempted-connection-limit.json` | `ConformanceSessionTest` > `S-09 ...` |
| S-10 | connection.preempted, reason quota_exceeded | `ws-sessions/preempted-quota-exceeded.json` | `ConformanceSessionTest` > `S-10 ...` |
| S-11 | Preempted before the first subscribe: the frame comes in v2 form | `ws-sessions/v2-preempt-before-subscribe.json` | `ConformanceSessionTest` > `S-11 ...` |
| S-12 | Error for an unreadable message comes in v2 form | `ws-sessions/v2-invalid-json-error.json` | `ConformanceSessionTest` > `S-12 ...` |
| S-13 | connection.error HANDSHAKE_FAILED | `ws-sessions/handshake-failed.json` | `ConformanceSessionTest` > `S-13 ...` |
| S-14 | connection.error SEARCH_FAILED | `ws-sessions/search-failed.json` | `ConformanceSessionTest` > `S-14 ...` |
| S-15 | connection.error INTERNAL_ERROR | `ws-sessions/internal-error.json` | `ConformanceSessionTest` > `S-15 ...` |
| S-16 | No pong for 60 s | `ws-sessions/pong-watchdog.json` | `ConformanceSessionTest` > `S-16 ...` |
| S-17 | Raw stream: admitted, no initial push, skeleton frames | `ws-sessions/raw-stream.json` | `ConformanceSessionTest` > `S-17 ...` |
| S-18 | Proactive rotation before the 2 h connection lifetime | `ws-sessions/rotation.json` | `ConformanceSessionTest` > `S-18 ...` |
| S-19 | Dial refused: no API key | `ws-sessions/dial-missing-key.json` | `ConformanceSessionTest` > `S-19 ...` |
| S-20 | Dial refused: invalid API key | `ws-sessions/dial-invalid-key.json` | `ConformanceSessionTest` > `S-20 ...` |
| S-21 | Dial refused: API key in the wrong format | `ws-sessions/dial-malformed-key.json` | `ConformanceSessionTest` > `S-21 ...` |
| S-22 | Dial refused: plan without WebSockets | `ws-sessions/dial-no-websockets.json` | `ConformanceSessionTest` > `S-22 ...` |
| S-23 | Dial refused: connection limit reached, no takeover | `ws-sessions/dial-too-many-connections.json` | `ConformanceSessionTest` > `S-23 ...` |
| S-24 | Dial refused: WebSocket quota exhausted | `ws-sessions/dial-quota-exceeded.json` | `ConformanceSessionTest` > `S-24 ...` |
| S-25 | Dial refused: lost the admit race | `ws-sessions/dial-admit-race.json` | `ConformanceSessionTest` > `S-25 ...` |
| S-26 | Dial refused: server error | `ws-sessions/dial-server-error.json` | `ConformanceSessionTest` > `S-26 ...` |
| S-27 | connection.preempted, reason quota_exceeded, with quotaReconnect turned off | `ws-sessions/preempted-quota-exceeded-no-reconnect.json` | `ConformanceSessionTest` > `S-27 ...` |
| S-28 | Dial refused: WebSocket quota exhausted, with quotaReconnect turned off | `ws-sessions/dial-quota-exceeded-no-reconnect.json` | `ConformanceSessionTest` > `S-28 ...` |
