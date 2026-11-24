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

  /**
   * JVM-1: a rotation dial refused because the plan's only connection is in use closes the old
   * connection and dials again at once: no long wait, no onError, reason rotation.
   */
  @Test
  void rotationAtTheConnectionLimitRedialsAtOnce() throws Exception {
    String tooMany =
        "{\"message\":\"Too many concurrent connections. Use x-takeover: true header to replace"
            + " an existing connection.\"}";
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      WsServer.Conn c1 = ws.nextDial("dial").conn();
      c1.nextSubscribe();
      c1.send(ADMITTED);
      StreamProbe.waitFor(() -> p.admitted.size() == 1, "admitted");
      ws.script.add(new WsServer.Refusal(403, tooMany));
      p.advance(115 * 60 * 1000);
      assertNull(ws.nextDial("refused rotation dial").conn());
      WsServer.Conn c2 = null;
      while (c2 == null) {
        c2 = ws.nextDial("redial").conn();
      }
      assertFalse(c1.open, "the old connection was closed before the redial");
      assertEquals(1000, c1.closeCodeFromClient);
      c2.nextSubscribe();
      c2.send(ADMITTED);
      StreamProbe.waitFor(() -> p.reconnects.size() == 1, "onReconnect");
      assertEquals(ReconnectReason.ROTATION, p.reconnects.get(0).reason());
      for (StreamProbe.Scheduled sc : p.scheduled) {
        assertTrue(sc.delayMs() < 60_000, "no long wait: " + sc);
        assertEquals(ReconnectReason.ROTATION, sc.reason());
      }
      p.settle();
      assertTrue(p.errors.isEmpty(), "no onError: " + p.errors);
      assertFalse(p.done);
      p.stop();
    }
  }

  /**
   * A quota refusal of the rotation dial with quota reconnect on does not end the stream either:
   * close the old connection, dial again at once, no quota wait. With quota reconnect off it ends.
   */
  @Test
  void rotationRefusedForQuotaRedialsAtOnce() throws Exception {
    String quota = "{\"message\":\"WebSocket quota exceeded for the current period\"}";
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      WsServer.Conn c1 = ws.nextDial("dial").conn();
      c1.nextSubscribe();
      c1.send(ADMITTED);
      StreamProbe.waitFor(() -> p.admitted.size() == 1, "admitted");
      ws.script.add(new WsServer.Refusal(403, quota));
      p.advance(115 * 60 * 1000);
      assertNull(ws.nextDial("refused rotation dial").conn());
      WsServer.Conn c2 = null;
      while (c2 == null) {
        c2 = ws.nextDial("redial").conn();
      }
      assertFalse(c1.open, "the old connection was closed before the redial");
      c2.nextSubscribe();
      c2.send(ADMITTED);
      StreamProbe.waitFor(() -> p.reconnects.size() == 1, "onReconnect");
      assertEquals(ReconnectReason.ROTATION, p.reconnects.get(0).reason());
      p.settle();
      assertTrue(p.quotaWaits.isEmpty(), "no quota wait: " + p.quotaWaits);
      assertTrue(p.errors.isEmpty(), "no onError: " + p.errors);
      p.stop();
    }
    try (WsServer ws = new WsServer()) {
      StreamProbe p =
          start(ws, false, StreamParams.builder().build().toJson(), b -> b.quotaReconnect(false));
      WsServer.Conn c1 = ws.nextDial("dial").conn();
      c1.nextSubscribe();
      c1.send(ADMITTED);
      StreamProbe.waitFor(() -> p.admitted.size() == 1, "admitted");
      ws.script.add(new WsServer.Refusal(403, quota));
      p.advance(115 * 60 * 1000);
      StreamProbe.waitFor(() -> p.done, "the end");
      assertInstanceOf(ForbiddenException.class, p.terminal);
      p.stop();
    }
  }

  /**
   * R-WS-2: connection.admitted comes before the subscribe is checked, so admitted followed by
   * SEARCH_FAILED keeps growing the backoff. Only an article or a pong starts it over.
   */
  @Test
  void onlyAnArticleOrAPongResetsTheBackoff() throws Exception {
    String searchFailed =
        "{\"event\":\"connection.error\",\"payloadVersion\":3,\"data\":{\"code\":\"SEARCH_FAILED\","
            + "\"message\":\"x\"}}";
    String pong =
        "{\"event\":\"connection.pong\",\"payloadVersion\":3,\"data\":{\"t\":1,\"serverNow\":1}}";
    try (WsServer ws = new WsServer()) {
      ws.autoPong = false; // a pong resets the backoff
      StreamProbe p =
          start(
              ws,
              false,
              StreamParams.builder().build().toJson(),
              b -> b.searchFailedThreshold(100));
      WsServer.Conn[] c = {ws.nextDial("dial").conn()};
      c[0].nextSubscribe();
      for (int i = 0; i < 5; i++) {
        c[0].send(ADMITTED);
        c[0].send(searchFailed);
        int n = i + 1;
        StreamProbe.waitFor(() -> p.scheduled.size() == n, "reconnect " + n);
        long low = Math.min(1000L << i, 30_000) / 2;
        long d = p.scheduled.get(i).delayMs();
        assertTrue(d >= low, "failure " + n + ": delay " + d + " ms, want at least " + low);
        c[0] = redialAfterTheWait(ws, p);
      }
      // An article starts the backoff over.
      c[0].send(ADMITTED);
      c[0].send(frame("article.created", article("A", null, "2026-11-23T10:00:00.000Z")));
      StreamProbe.waitFor(() -> p.items.size() == 1, "the article");
      c[0].close(1000);
      StreamProbe.waitFor(() -> p.scheduled.size() == 6, "reconnect after the article");
      assertTrue(p.scheduled.get(5).delayMs() <= 1000, "after an article: " + p.scheduled.get(5));
      // Two more failures, then a pong starts it over.
      for (int i = 0; i < 2; i++) {
        c[0] = redialAfterTheWait(ws, p);
        c[0].send(ADMITTED);
        c[0].send(searchFailed);
        int n = 7 + i;
        StreamProbe.waitFor(() -> p.scheduled.size() == n, "reconnect " + n);
      }
      c[0] = redialAfterTheWait(ws, p);
      c[0].send(ADMITTED);
      c[0].send(pong);
      StreamProbe.waitFor(() -> p.pongs.get() == 1, "the pong");
      c[0].close(1000);
      StreamProbe.waitFor(() -> p.scheduled.size() == 9, "reconnect after the pong");
      assertTrue(p.scheduled.get(8).delayMs() <= 1000, "after a pong: " + p.scheduled.get(8));
      p.stop();
    }
  }

  /** Advances to the last scheduled reconnect and returns the next accepted, subscribed conn. */
  private static WsServer.Conn redialAfterTheWait(WsServer ws, StreamProbe p)
      throws InterruptedException {
    StreamProbe.Scheduled s = p.scheduled.get(p.scheduled.size() - 1);
    p.advance(s.dueAt() - p.clock.millis());
    WsServer.Conn next = null;
    while (next == null) {
      next = ws.nextDial("redial").conn();
    }
    next.nextSubscribe();
    return next;
  }

  /** JVM-2: an Error thrown by a listener (Kotlin TODO(), AssertionError) does not stop events. */
  @Test
  void listenerErrorsDoNotKillDelivery() throws Exception {
    try (WsServer ws = new WsServer()) {
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      java.util.concurrent.atomic.AtomicInteger calls =
          new java.util.concurrent.atomic.AtomicInteger();
      p.onEventHook =
          e -> {
            if (calls.incrementAndGet() == 1) {
              throw new AssertionError("not implemented");
            }
          };
      WsServer.Conn c = ws.nextDial("dial").conn();
      c.send(frame("article.created", article("A", null, "2026-11-23T10:00:00.000Z")));
      c.send(frame("article.created", article("B", null, "2026-11-23T10:00:01.000Z")));
      StreamProbe.waitFor(() -> p.items.size() == 1, "the second article");
      assertEquals(2, calls.get());
      p.stop();
      assertTrue(p.done, "onClosed ran");
    }
  }

  /**
   * JVM-3: a blocked listener stops reading frames (back-pressure) but not the keep-alive: pings
   * still go out every 25 s and the watchdog does not replace the connection.
   */
  @Test
  void blockedListenerKeepsPinging() throws Exception {
    int total = 1100;
    try (WsServer ws = new WsServer()) {
      ws.autoPong = false;
      StreamProbe p = start(ws, false, StreamParams.builder().build().toJson(), b -> {});
      java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
      p.onEventHook =
          e -> {
            try {
              release.await();
            } catch (InterruptedException x) {
              Thread.currentThread().interrupt();
            }
          };
      WsServer.Conn c = ws.nextDial("dial").conn();
      c.nextSubscribe();
      Thread sender =
          new Thread(
              () -> {
                for (int i = 0; i < total; i++) {
                  c.send(
                      frame("article.created", article("A" + i, null, "2026-11-23T10:00:00.000Z")));
                }
              });
      sender.setDaemon(true);
      sender.start();
      // 1 running + 1024 queued + 1 parked; then the stream stops reading.
      StreamProbe.waitFor(() -> p.frames.get() >= 1026, "the listener queue to fill");
      Thread.sleep(200);
      assertEquals(1026, p.frames.get(), "no frame is read while the listener is behind");
      int before = p.pingsSent.get();
      p.advance(125_000);
      assertEquals(
          before + 5, p.pingsSent.get(), "a ping every 25 s while the listener is blocked");
      StreamProbe.waitFor(
          () ->
              c.received.stream().filter(n -> "ping".equals(n.path("action").asText())).count()
                  >= 5,
          "the pings at the server");
      assertTrue(p.scheduled.isEmpty(), "no reconnect: " + p.scheduled);
      assertEquals(1, ws.dialCount);
      release.countDown();
      StreamProbe.waitFor(
          () -> p.items.size() == total, "every article after the listener caught up");
      assertFalse(p.done);
      p.stop();
    }
  }

  /** JVM-7: maxMessageBytes counts UTF-8 bytes. */
  @Test
  void messageSizeCountsUtf8Bytes() {
    assertEquals(3, StreamEngine.utf8Length("abc"));
    assertEquals(2, StreamEngine.utf8Length("\u00e9"));
    assertEquals(3, StreamEngine.utf8Length("\u20ac"));
    assertEquals(4, StreamEngine.utf8Length("\ud83d\ude00"));
    assertEquals(9, StreamEngine.utf8Length("\u65e5\u672c\u8a9e"));
  }
}
