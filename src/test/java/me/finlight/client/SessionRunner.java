package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Plays a ws-sessions fixture against the SDK and checks every {@code expect} step. */
final class SessionRunner {
  /** Extra checks after the scripted steps. */
  interface After {
    void run(Ctx ctx) throws Exception;
  }

  /** What an {@link After} block can use. */
  static final class Ctx {
    StreamProbe probe;
    WsServer ws;
    ObjectNode subscribe;
    WsServer.@Nullable Conn conn;
    WsServer.@Nullable Dial firstDial;
  }

  private final JsonNode fixture;
  private List<String> forbidden = List.of();
  private boolean autoPong = true;
  private @Nullable After after;

  SessionRunner(JsonNode fixture) {
    this.fixture = fixture;
  }

  SessionRunner forbidden(String... keys) {
    this.forbidden = List.of(keys);
    return this;
  }

  SessionRunner autoPong(boolean on) {
    this.autoPong = on;
    return this;
  }

  SessionRunner after(After after) {
    this.after = after;
    return this;
  }

  private static boolean isSubscribe(JsonNode f) {
    return f.isObject() && f.has("payloadVersion") && !f.has("type") && !f.has("action");
  }

  private ObjectNode without(JsonNode frame, List<String> keys) {
    ObjectNode o = frame.deepCopy();
    keys.forEach(o::remove);
    return o;
  }

  void run() throws Exception {
    JsonNode steps = fixture.get("steps");
    boolean raw = "raw".equals(fixture.path("endpoint").asText());
    JsonNode first = null;
    JsonNode firstDial = null;
    for (JsonNode s : steps) {
      if (first == null
          && "clientSends".equals(s.get("step").asText())
          && isSubscribe(s.path("frame"))) {
        first = s.get("frame");
      }
      if (firstDial == null && "dial".equals(s.get("step").asText())) {
        firstDial = s;
      }
    }
    ObjectNode subscribe =
        first == null
            ? (ObjectNode) Kit.parse("{\"payloadVersion\":3,\"query\":\"ticker:XMPL\"}")
            : without(first, forbidden);
    ObjectNode params = without(subscribe, List.of("payloadVersion", "clientNonce"));
    String nonce =
        first != null && first.has("clientNonce") ? first.get("clientNonce").asText() : "c-test";
    String apiKey =
        firstDial != null && firstDial.path("expectRequestHeaders").has("x-api-key")
            ? firstDial.path("expectRequestHeaders").get("x-api-key").asText()
            : Kit.API_KEY;
    JsonNode cc = fixture.path("clientConfig");
    Consumer<FinlightConfig.Builder> config =
        b -> {
          if (cc.has("quotaReconnect")) {
            b.quotaReconnect(cc.get("quotaReconnect").asBoolean());
          }
        };

    try (WsServer ws = new WsServer()) {
      ws.autoPong = autoPong;
      for (JsonNode s : steps) {
        if ("dial".equals(s.get("step").asText())) {
          JsonNode server = s.get("server");
          int status = server.get("status").asInt();
          ws.script.add(new WsServer.Refusal(status, server.path("body").asText("")));
        }
      }
      StreamProbe probe = new StreamProbe(ws, nonce);
      probe.start(apiKey, raw, params, StreamOptions.defaults(), config);
      Ctx ctx = new Ctx();
      ctx.probe = probe;
      ctx.ws = ws;
      ctx.subscribe = subscribe;
      WsServer.Conn subscribedOn = null;
      int[] c = new int[6]; // admitted, items, pongs, scheduled, errors, duplicates
      int quota = 0;
      try {
        for (int i = 0; i < steps.size(); i++) {
          JsonNode step = steps.get(i);
          String kind = step.get("step").asText();
          switch (kind) {
            case "dial" -> {
              WsServer.Dial d = ws.nextDial("dial (step " + i + ")");
              assertEquals(raw ? "/raw" : "/", d.path(), "dial path");
              String requestLine = d.head().substring(0, d.head().indexOf("\r\n"));
              assertFalse(requestLine.toLowerCase(java.util.Locale.ROOT).contains("apikey="));
              JsonNode key = step.path("expectRequestHeaders").get("x-api-key");
              if (key != null) {
                assertEquals(key.asText(), d.headers().get("x-api-key"));
              }
              if (ctx.firstDial == null) {
                ctx.firstDial = d;
              }
              ctx.conn = d.conn();
            }
            case "clientSends" -> {
              JsonNode frame = step.get("frame");
              JsonNode match = step.get("match");
              WsServer.Conn conn = ctx.conn;
              if (match != null) {
                JsonNode msg =
                    conn.next(
                        "client frame matching " + match,
                        n -> {
                          for (String f : Kit.fieldNames(match)) {
                            if (!match.get(f).equals(n.get(f))) {
                              return false;
                            }
                          }
                          return true;
                        });
                if ("ping".equals(match.path("action").asText())) {
                  assertTrue(msg.get("t").isNumber(), "ping carries t as a number");
                }
                break;
              }
              if (frame == null || frame.isTextual()) {
                break; // a broken frame no SDK sends
              }
              if ("ping".equals(frame.path("action").asText())) {
                break; // a ping may or may not precede the subscribe
              }
              if (!isSubscribe(frame) || subscribedOn == conn) {
                break; // the SDK subscribes once per connection
              }
              JsonNode msg = conn.nextSubscribe();
              assertEquals(without(frame, forbidden), msg, "subscribe frame");
              assertTrue(msg.get("payloadVersion").isInt(), "payloadVersion is a JSON integer");
              assertEquals(3, msg.get("payloadVersion").intValue());
              for (String f : forbidden) {
                assertFalse(msg.has(f), "the SDK never sends " + f);
              }
              subscribedOn = conn;
            }
            case "serverSends" -> {
              int before = probe.frames.get();
              ctx.conn.send(step.get("frame"));
              StreamProbe.waitFor(
                  () -> probe.frames.get() > before || probe.done, "the client to read a frame");
              probe.settle();
            }
            case "serverCloses" -> {
              if (ctx.conn != null && ctx.conn.open) {
                ctx.conn.close(1000);
              }
              Thread.sleep(30);
              probe.settle();
            }
            case "advance" -> probe.advance(step.get("ms").asLong());
            case "expect" ->
                quota = expect(step, i + 1 < steps.size() ? steps.get(i + 1) : null, ctx, c, quota);
            default -> throw new AssertionError("unknown step " + kind);
          }
        }
        if (after != null) {
          after.run(ctx);
        }
      } finally {
        probe.stop();
      }
      // Stopping cancels every timer and closes every socket (R-WS-6).
      assertEquals(0, probe.clock.pending(), "timers left after stop");
      StreamProbe.waitFor(() -> ws.openConnections() == 0, "all sockets closed");
      StreamProbe.waitFor(() -> probe.stream.engine().loopTerminated(), "the stream thread to end");
    }
  }

  private int expect(JsonNode step, @Nullable JsonNode next, Ctx ctx, int[] c, int quota)
      throws Exception {
    StreamProbe p = ctx.probe;
    String outcome = step.get("outcome").asText();
    switch (outcome) {
      case "admitted" -> {
        StreamProbe.waitFor(() -> p.admitted.size() > c[0], "admitted");
        AdmittedInfo a = p.admitted.get(c[0]++);
        if (step.has("leaseId")) {
          assertEquals(step.get("leaseId").asText(), a.leaseId());
        }
        if (step.has("clientNonce")) {
          assertEquals(step.get("clientNonce").asText(), a.clientNonce());
        }
      }
      case "noAdmitted" -> {
        p.settle();
        Thread.sleep(20);
        assertEquals(c[0], p.admitted.size(), "no admitted frame");
        assertNull(p.terminal);
      }
      case "item" -> {
        StreamProbe.waitFor(() -> p.articleItems().size() > c[1], "an item");
        StreamEvent item = p.articleItems().get(c[1]++);
        assertEquals(step.get("kind").asText(), item.kind().value());
        if (step.has("id")) {
          assertEquals(step.get("id").asText(), item.data().id());
        }
      }
      case "duplicateDropped" -> {
        StreamProbe.waitFor(() -> p.duplicates.size() > c[5], "a dropped duplicate");
        String id = p.duplicates.get(c[5]++);
        if (step.has("id")) {
          assertEquals(step.get("id").asText(), id);
        }
        p.settle();
        assertEquals(c[1], p.articleItems().size(), "the duplicate was not delivered");
      }
      case "liveness" -> {
        StreamProbe.waitFor(() -> p.pongs.get() > c[2], "a pong refreshing the watchdog");
        c[2] = p.pongs.get();
        assertNull(p.terminal);
        assertEquals(c[3], p.scheduled.size(), "no reconnect");
      }
      case "logOnly" -> {
        StreamProbe.waitFor(
            () ->
                p.errors.size() > c[4]
                    || p.items.stream().anyMatch(x -> x.kind() == StreamEventKind.OTHER),
            "a reported error or an 'other' item");
        if (step.has("code")) {
          String code = step.get("code").asText();
          assertTrue(
              p.errors.subList(c[4], p.errors.size()).stream()
                  .anyMatch(
                      e ->
                          e instanceof FinlightApiException a
                              && a.code() != null
                              && code.equals(a.code().value())),
              "an error with code " + code + " was reported");
        }
        c[4] = p.errors.size();
        p.settle();
        assertNull(p.terminal);
        assertFalse(p.done, "the stream goes on");
        assertEquals(c[3], p.scheduled.size(), "no reconnect");
        assertTrue(ctx.conn != null && ctx.conn.open, "the connection stays open");
      }
      case "reported" -> {
        assertEquals("onQuotaWait", step.get("hook").asText());
        int q = quota;
        StreamProbe.waitFor(() -> p.quotaWaits.size() > q, "onQuotaWait");
        checkQuotaWait(p.quotaWaits.get(quota), step);
        quota++;
        assertFalse(p.done);
      }
      case "terminalError" -> {
        StreamProbe.waitFor(() -> p.done, "a terminal error");
        FinlightException err = p.terminal;
        assertNotNull(err, "the stream ended with an error");
        checkError(err, step);
        assertEquals(c[3], p.scheduled.size(), "no reconnect after a terminal error");
      }
      case "noReconnect" -> {
        int dials = ctx.ws.dialCount;
        p.advance(0);
        Thread.sleep(40);
        assertEquals(dials, ctx.ws.dialCount, "no new dial");
        if (p.done) {
          assertEquals(c[3], p.scheduled.size(), "nothing scheduled after the end");
        }
      }
      case "reconnect" -> {
        StreamProbe.waitFor(() -> p.scheduled.size() > c[3], "a scheduled reconnect");
        StreamProbe.Scheduled s = p.scheduled.get(c[3]++);
        assertFalse(p.done, "the stream goes on");
        JsonNode backoff = step.get("backoff");
        if (backoff != null && backoff.isTextual() && backoff.asText().equals("long")) {
          assertTrue(s.delayMs() >= 60_000, "long backoff, got " + s.delayMs());
        }
        if (backoff != null && backoff.isTextual() && backoff.asText().equals("quota")) {
          assertEquals(3_600_000, s.delayMs(), "quota wait");
        }
        if (backoff != null && backoff.isBoolean() && backoff.asBoolean()) {
          assertTrue(s.delayMs() > 0, "a backoff delay");
        }
        if (step.has("firstRetryMinMs")) {
          assertTrue(s.delayMs() >= step.get("firstRetryMinMs").asLong(), "first retry delay");
        }
        if (step.has("reason")) {
          assertEquals(step.get("reason").asText(), s.reason().value());
        }
        if ("takeover".equals(step.path("hint").asText())) {
          StreamProbe.waitFor(() -> !p.errors.isEmpty(), "the takeover hint");
          assertTrue(
              p.errors.stream().anyMatch(e -> e.getMessage().contains("takeover")),
              "an error carries the takeover hint");
        }
        if (step.has("reported")) {
          int q = quota;
          StreamProbe.waitFor(() -> p.quotaWaits.size() > q, "onQuotaWait");
          checkQuotaWait(p.quotaWaits.get(quota), step.get("reported"));
          quota++;
        }
        int dials = ctx.ws.dialCount;
        long now = p.clock.millis();
        if (now < s.dueAt()) {
          p.advance(s.dueAt() - now - 1);
          Thread.sleep(20);
          assertEquals(dials, ctx.ws.dialCount, "no dial before the delay");
          p.advance(1);
        }
        if (next == null || !"dial".equals(next.get("step").asText())) {
          WsServer.Dial d = ctx.ws.nextDial("the redial");
          ctx.conn = d.conn();
          if (step.path("resubscribe").asBoolean() && d.conn() != null) {
            JsonNode msg = d.conn().nextSubscribe();
            assertEquals(
                without(ctx.subscribe, List.of("clientNonce")),
                without(msg, List.of("clientNonce")),
                "the same subscribe after a reconnect");
          }
        }
      }
      default -> throw new AssertionError("unknown outcome " + outcome);
    }
    return quota;
  }

  private static void checkQuotaWait(QuotaWaitInfo info, JsonNode expected) {
    if (expected.has("attempt")) {
      assertEquals(expected.get("attempt").asInt(), info.attempt());
    }
    assertEquals(3_600_000, info.nextAttemptIn().toMillis());
    checkError(info.error(), expected);
  }

  static void checkError(FinlightException err, JsonNode expected) {
    String type = expected.get("error").asText();
    if ("PreemptedError".equals(type)) {
      PreemptedException p = assertInstanceOf(PreemptedException.class, err);
      assertEquals(FinlightErrorKind.PREEMPTED, p.kind());
      assertEquals(expected.get("reason").asText(), p.reason().value());
      JsonNode lease = expected.get("newLeaseId");
      if (lease != null) {
        assertEquals(lease.isNull() ? null : lease.asText(), p.newLeaseId());
      }
    } else if ("FinlightApiError".equals(type)) {
      FinlightApiException a = assertInstanceOf(FinlightApiException.class, err);
      assertEquals(FinlightErrorKind.API, a.kind());
      if (expected.has("apiKind")) {
        assertEquals(expected.get("apiKind").asText(), a.apiKind().value());
        assertEquals(ErrorChecks.classFor(a.apiKind()), a.getClass());
      }
      if (expected.has("status")) {
        assertEquals(expected.get("status").asInt(), a.status());
      }
      if (expected.has("code")) {
        assertNotNull(a.code());
        assertEquals(expected.get("code").asText(), a.code().value());
      }
      if (a.apiKind() == ApiErrorKind.VALIDATION_FAILED) {
        assertFalse(a.errors().isEmpty(), "errors[] from details[]");
        assertNotNull(a.errors().get(0).field());
        assertFalse(a.errors().get(0).constraints().isEmpty(), "constraints from details[]");
      }
    } else {
      throw new AssertionError("unknown error type " + type);
    }
  }
}
