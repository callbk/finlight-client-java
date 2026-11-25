package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/** Kit cases S-01 to S-29 and R-13, R-14, against the in-process WebSocket server. */
class ConformanceSessionTest {
  static final Map<String, Executable> CASES = new LinkedHashMap<>();

  static {
    for (int i = 1; i <= 29; i++) {
      String id = String.format(java.util.Locale.ROOT, "S-%02d", i);
      CASES.put(id, () -> session(id));
    }
    CASES.put("R-13", () -> subscribeFrame("R-13", false));
    CASES.put("R-14", () -> subscribeFrame("R-14", true));
  }

  static Set<String> ids() {
    return CASES.keySet();
  }

  @TestFactory
  Stream<DynamicNode> cases() {
    Map<String, String> titles = Kit.cases();
    return CASES.entrySet().stream()
        .map(e -> DynamicTest.dynamicTest(e.getKey() + " " + titles.get(e.getKey()), e.getValue()));
  }

  private static void session(String id) throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf(id));
    SessionRunner runner = new SessionRunner(f);
    switch (id) {
      case "S-02" -> runner.after(ctx -> rawHeaderBytes(ctx.firstDial.head()));
      case "S-07" -> {
        runner.forbidden("includeContent");
        for (String legacy : new String[] {"includeContent", "includeEntities", "extended"}) {
          assertTrue(
              Arrays.stream(StreamParams.Builder.class.getMethods())
                  .noneMatch(m -> m.getName().equals(legacy)),
              "StreamParams cannot carry " + legacy);
        }
      }
      case "S-08" -> {
        runner.forbidden("include");
        assertTrue(
            Arrays.stream(RawStreamParams.Builder.class.getMethods())
                .noneMatch(m -> m.getName().equals("include")),
            "rawStream has no include: rejected before dialing, at compile time");
      }
      case "S-14" -> runner.after(ConformanceSessionTest::searchFailedSurfaces);
      case "S-29" -> {
        unknownSourceTypeGoesToTheServer(f);
        runner.after(ConformanceSessionTest::allowedValuesFromDetails);
      }
      case "S-16" -> runner.autoPong(false);
      default -> {}
    }
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(60), runner::run);
  }

  /**
   * S-29: SourceType is an open vocabulary, so the SDK sends an unknown value as it is and the
   * server's VALIDATION_FAILED ends the stream (checked by the session run).
   */
  private static void unknownSourceTypeGoesToTheServer(JsonNode f) {
    for (JsonNode s : f.get("steps")) {
      if ("clientSends".equals(s.get("step").asText())) {
        com.fasterxml.jackson.databind.node.ObjectNode args = s.get("frame").deepCopy();
        args.remove(List.of("payloadVersion", "clientNonce"));
        assertEquals(args, SdkCalls.stream(args).toJson(), "the builder keeps the unknown value");
        assertFalse(SourceType.of("wire").isKnown());
        return;
      }
    }
    throw new AssertionError("S-29 has no subscribe frame");
  }

  /** S-29: the details[] entry reaches errors() with field and allowedValues. */
  private static void allowedValuesFromDetails(SessionRunner.Ctx ctx) {
    FinlightException t = ctx.probe.terminal;
    ValidationFailedException v =
        org.junit.jupiter.api.Assertions.assertInstanceOf(ValidationFailedException.class, t);
    assertEquals(1, v.errors().size());
    FieldIssue issue = v.errors().get(0);
    assertEquals("sourceTypes", issue.field());
    assertEquals(SourceTypeValues.ALL, issue.allowedValues());
  }

  /** S-02 (R-WS-3): the raw dial bytes carry lowercase header names and no key in the URL. */
  static void rawHeaderBytes(String head) {
    String requestLine = head.substring(0, head.indexOf("\r\n"));
    assertFalse(requestLine.toLowerCase(java.util.Locale.ROOT).contains("apikey"), requestLine);
    assertTrue(
        head.contains("\r\nx-api-key: " + Kit.API_KEY + "\r\n"), "lowercase x-api-key:\n" + head);
    assertFalse(head.contains("X-Api-Key") || head.contains("X-API-KEY"));
    assertTrue(head.contains("\r\nx-client-version: " + FinlightClient.VERSION + "\r\n"));
    assertTrue(
        head.contains("\r\nUser-Agent: finlight-client-java/" + FinlightClient.VERSION + "\r\n"));
    assertFalse(head.contains("x-takeover"), "takeover is off by default");
  }

  /** S-14: the fifth SEARCH_FAILED in a row goes to onError; the stream goes on. */
  private static void searchFailedSurfaces(SessionRunner.Ctx ctx) throws Exception {
    StreamProbe p = ctx.probe;
    JsonNode error =
        Kit.parse(
            "{\"event\":\"connection.error\",\"payloadVersion\":3,\"data\":{\"code\":\"SEARCH_FAILED\",\"message\":\"x\"}}");
    assertTrue(
        p.errors.stream().noneMatch(ConformanceSessionTest::searchFailed), "not surfaced after 1");
    for (int n = 2; n <= 5; n++) {
      int scheduled = p.scheduled.size();
      ctx.conn.send(error);
      StreamProbe.waitFor(() -> p.scheduled.size() > scheduled, "a reconnect");
      if (n < 5) {
        assertTrue(
            p.errors.stream().noneMatch(ConformanceSessionTest::searchFailed),
            "not surfaced after " + n);
      }
      StreamProbe.Scheduled s = p.scheduled.get(p.scheduled.size() - 1);
      assertTrue(s.delayMs() > 0);
      p.advance(Math.max(0, s.dueAt() - p.clock.millis()));
      ctx.conn = ctx.ws.nextDial("redial " + n).conn();
    }
    StreamProbe.waitFor(
        () -> p.errors.stream().anyMatch(ConformanceSessionTest::searchFailed),
        "the error after 5");
    assertEquals(1, p.errors.stream().filter(ConformanceSessionTest::searchFailed).count());
    assertFalse(p.done);
  }

  private static boolean searchFailed(FinlightException e) {
    return e instanceof FinlightApiException a
        && a.code() != null
        && a.code().value().equals("SEARCH_FAILED");
  }

  /** R-13, R-14: the exact subscribe frame and the server's answer. */
  private static void subscribeFrame(String id, boolean raw) throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf(id));
    JsonNode frame = f.get("frame");
    try (WsServer ws = new WsServer()) {
      StreamProbe probe = new StreamProbe(ws, frame.get("clientNonce").asText());
      JsonNode args = f.at("/sdk/args");
      com.fasterxml.jackson.databind.node.ObjectNode params =
          raw ? SdkCalls.rawStream(args).toJson() : SdkCalls.stream(args).toJson();
      probe.start(Kit.API_KEY, raw, params, StreamOptions.defaults(), b -> {});
      try {
        WsServer.Dial dial = ws.nextDial("dial");
        assertEquals(raw ? "/raw" : "/", dial.path());
        JsonNode sent = dial.conn().nextSubscribe();
        assertEquals(frame, sent, "exactly the kit frame");
        assertTrue(sent.get("payloadVersion").isInt());
        assertFalse(sent.has("action"), "the subscribe goes to $default");
        for (String legacy : new String[] {"includeContent", "includeEntities", "extended"}) {
          assertFalse(sent.has(legacy));
        }
        if (raw) {
          assertFalse(sent.has("include"));
        }
        for (JsonNode out : f.at("/server/frames")) {
          dial.conn().send(out);
        }
        StreamProbe.waitFor(() -> probe.admitted.size() == 1, "admitted");
        if (!raw) {
          StreamProbe.waitFor(() -> probe.articleItems().size() == 1, "the initial push");
          assertEquals(StreamEventKind.CREATED, probe.articleItems().get(0).kind());
        } else {
          probe.settle();
          assertEquals(0, probe.items.size(), "no initial push on raw");
        }
        assertTrue(probe.errors.isEmpty());
      } finally {
        probe.stop();
      }
    }
  }
}
