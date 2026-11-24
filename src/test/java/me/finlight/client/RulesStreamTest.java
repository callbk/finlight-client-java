package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** R-WS-1 to R-WS-6 beyond the kit sessions. */
@Timeout(60)
class RulesStreamTest {
  private static final String ADMITTED =
      "{\"event\":\"connection.admitted\",\"payloadVersion\":3,\"data\":{\"leaseId\":\"l1\",\"serverNow\":1,\"clientNonce\":\"n\"}}";

  private static ObjectNode article(String id, String availableAt, String firstSeenAt) {
    ObjectNode a = Kit.load("fixtures/decode/full-article.json").get("wire").deepCopy();
    a.put("id", id);
    a.put("link", "https://news.example.com/" + id);
    if (availableAt == null) {
      a.putNull("availableAt");
    } else {
      a.put("availableAt", availableAt);
    }
    a.put("firstSeenAt", firstSeenAt);
    return a;
  }

  private static String frame(String event, JsonNode data) {
    return "{\"event\":\"" + event + "\",\"payloadVersion\":3,\"data\":" + data + "}";
  }

  private static StreamProbe start(
      WsServer ws, boolean raw, ObjectNode params, Consumer<FinlightConfig.Builder> cfg) {
    return new StreamProbe(ws, "n").start(Kit.API_KEY, raw, params, StreamOptions.defaults(), cfg);
  }

  @Test
  void onlySetKeysPlusPayloadVersionAndNonce() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p =
          start(ws, false, StreamParams.builder().query("ticker:X").build().toJson(), b -> {});
      JsonNode sub = ws.nextDial("dial").conn().nextSubscribe();
      assertEquals(
          Kit.parse("{\"payloadVersion\":3,\"query\":\"ticker:X\",\"clientNonce\":\"n\"}"), sub);
      assertTrue(sub.get("payloadVersion").isInt());
      p.stop();
    }
  }

  @Test
  void takeoverHeaderOnlyWhenAsked() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p =
          new StreamProbe(ws, "n")
              .start(
                  Kit.API_KEY,
                  true,
                  RawStreamParams.builder().build().toJson(),
                  StreamOptions.takeover(true),
                  b -> {});
      WsServer.Dial d = ws.nextDial("dial");
      assertEquals("/raw", d.path());
      assertTrue(d.head().contains("\r\nx-takeover: true\r\n"), "lowercase x-takeover");
      p.stop();
    }
  }

  /** R-WS-1: v2 article frames are never delivered; v2 pong refreshes the watchdog. */
  @Test
  void legacyArticleFramesAreNotDelivered() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      WsServer.Conn c = ws.nextDial("dial").conn();
      c.send("{\"action\":\"sendArticle\",\"data\":{\"link\":\"x\",\"title\":\"t\"}}");
      c.send("{\"action\":\"pong\",\"t\":1}");
      StreamProbe.waitFor(() -> p.frames.get() >= 2, "frames");
      p.settle();
      assertEquals(0, p.items.size());
      assertEquals(1, p.pongs.get());
      p.stop();
    }
  }

  /** R-WS-5 across a rotation, R-WS-6 lastSeenAt, and a throwing listener. */
  @Test
  void dedupeSurvivesRotationAndListenerErrorsAreContained() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      p.onEventHook =
          e -> {
            throw new IllegalStateException("user code failed");
          };
      WsServer.Conn c1 = ws.nextDial("dial").conn();
      c1.send(ADMITTED);
      ObjectNode a = article("A", null, "2026-11-23T10:00:00.000Z");
      c1.send(frame("article.created", a));
      StreamProbe.waitFor(() -> p.frames.get() >= 2, "the article");
      p.settle();
      Thread.sleep(20);
      assertFalse(p.done, "a listener exception does not end the stream");
      assertEquals(
          "2026-11-23T10:00:00.000Z",
          p.stream.lastSeenAt(),
          "firstSeenAt when availableAt is null");
      p.onEventHook = null;
      p.advance(115 * 60 * 1000);
      WsServer.Conn c2 = ws.nextDial("rotation dial").conn();
      c2.nextSubscribe();
      c2.send(ADMITTED);
      c2.send(frame("article.created", a)); // the initial push repeats the article
      StreamProbe.waitFor(() -> p.duplicates.size() == 1, "the repeat dropped");
      StreamProbe.waitFor(() -> p.reconnects.size() == 1, "onReconnect");
      ReconnectInfo info = p.reconnects.get(0);
      assertEquals(ReconnectReason.ROTATION, info.reason());
      assertEquals(2, info.connection());
      assertEquals("2026-11-23T10:00:00.000Z", info.lastSeenAt());
      StreamProbe.waitFor(
          () -> !c1.open, "the old connection closed after the new one was admitted");
      assertEquals(1000, c1.closeCodeFromClient);
      c2.send(frame("article.updated", a));
      StreamProbe.waitFor(() -> p.items.size() == 1, "updated is not a duplicate of created");
      p.stop();
    }
  }

  @Test
  void rawStreamsReportFirstSeenAt() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, true, RawStreamParams.builder().build().toJson(), b -> {});
      WsServer.Conn c1 = ws.nextDial("dial").conn();
      c1.send(
          frame(
              "article.created",
              article("R", "2026-11-23T12:00:00.000Z", "2026-11-23T11:00:00.000Z")));
      StreamProbe.waitFor(() -> p.items.size() == 1, "item");
      c1.close(1000);
      StreamProbe.waitFor(() -> !p.scheduled.isEmpty(), "a reconnect");
      StreamProbe.Scheduled s = p.scheduled.get(0);
      assertEquals(ReconnectReason.CLOSED, s.reason());
      p.advance(s.dueAt() - p.clock.millis());
      ws.nextDial("redial");
      StreamProbe.waitFor(() -> p.reconnects.size() == 1, "onReconnect");
      assertEquals("2026-11-23T11:00:00.000Z", p.reconnects.get(0).lastSeenAt());
      p.stop();
    }
  }

  @Test
  void stopClosesWith1000AndEndsEveryThread() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      WsServer.Conn c = ws.nextDial("dial").conn();
      c.nextSubscribe();
      p.stop();
      assertTrue(p.done);
      assertNull(p.terminal);
      assertTrue(p.stream.closed());
      StreamProbe.waitFor(() -> !c.open, "socket closed");
      assertEquals(1000, c.closeCodeFromClient);
      assertEquals(0, p.clock.pending(), "no timer left");
      StreamProbe.waitFor(() -> p.stream.engine().loopTerminated(), "loop thread ended");
      StreamProbe.waitFor(
          () ->
              Thread.getAllStackTraces().keySet().stream()
                  .noneMatch(t -> t.isAlive() && t.getName().startsWith(p.stream.engine().name())),
          "threads ended");
    }
  }

  @Test
  void close1008IsTerminal() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      WsServer.Conn c = ws.nextDial("dial").conn();
      c.close(1008);
      StreamProbe.waitFor(() -> p.done, "the end");
      FinlightApiException e = assertInstanceOf(FinlightApiException.class, p.terminal);
      assertEquals(1008, e.status());
      assertTrue(p.scheduled.isEmpty());
      p.stop();
    }
  }

  /** R-WS-2: a 403 without a readable body gets a long wait plus onError; a bare 401 ends. */
  @Test
  void refusalsWithoutBody() throws Exception {
    try (WsServer ws = new WsServer()) {
      ws.script.add(new WsServer.Refusal(403, ""));
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      ws.nextDial("dial");
      StreamProbe.waitFor(() -> !p.scheduled.isEmpty(), "a reconnect");
      assertTrue(p.scheduled.get(0).delayMs() >= 60_000, "long wait");
      assertEquals(ReconnectReason.DIAL_REFUSED, p.scheduled.get(0).reason());
      StreamProbe.waitFor(() -> !p.errors.isEmpty(), "onError");
      assertInstanceOf(ForbiddenException.class, p.errors.get(0));
      p.stop();
    }
    try (WsServer ws = new WsServer()) {
      ws.script.add(new WsServer.Refusal(401, ""));
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      StreamProbe.waitFor(() -> p.done, "the end");
      assertInstanceOf(UnauthorizedException.class, p.terminal);
      p.stop();
    }
    try (WsServer ws = new WsServer()) {
      ws.script.add(new WsServer.Refusal(429, ""));
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      StreamProbe.waitFor(() -> !p.scheduled.isEmpty(), "a reconnect");
      assertTrue(p.scheduled.get(0).delayMs() >= 60_000);
      assertEquals(ReconnectReason.RATE_LIMITED, p.scheduled.get(0).reason());
      p.stop();
    }
  }

  /**
   * The quota wait repeats every hour and counts its attempts; an admitted connection resets it.
   */
  @Test
  void quotaWaitsRepeatHourly() throws Exception {
    String quota = "{\"message\":\"WebSocket quota exceeded for the current period\"}";
    try (WsServer ws = new WsServer()) {
      ws.script.add(new WsServer.Refusal(403, quota));
      ws.script.add(new WsServer.Refusal(403, quota));
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      StreamProbe.waitFor(() -> p.quotaWaits.size() == 1, "first wait");
      p.advance(3_600_000);
      StreamProbe.waitFor(() -> p.quotaWaits.size() == 2, "second wait");
      assertEquals(2, p.quotaWaits.get(1).attempt());
      assertTrue(p.errors.isEmpty(), "quota waits do not go to onError");
      p.advance(3_600_000);
      WsServer.Conn c = null;
      while (c == null) {
        c = ws.nextDial("dial").conn();
      }
      c.send(ADMITTED);
      StreamProbe.waitFor(() -> p.admitted.size() == 1, "admitted");
      assertFalse(p.done);
      p.stop();
    }
  }
}
