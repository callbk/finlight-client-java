package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import me.finlight.client.internal.Json;
import me.finlight.client.model.Article;
import me.finlight.client.model.GetArticlesWebSocketParams;
import me.finlight.client.model.GetRawArticlesWebSocketParams;
import me.finlight.client.model.RawArticle;
import me.finlight.client.support.WsTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * End-to-end tests of the streaming protocol against an in-process WebSocket server, mirroring the
 * Go and .NET clients' coverage.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WebSocketProtocolTest {

  /** Keeps reconnect tests quick; mirrors the sibling clients' fast options. */
  private static WebSocketOptions.Builder fastOptions() {
    return WebSocketOptions.builder()
        .baseReconnectDelay(Duration.ofMillis(10))
        .maxReconnectDelay(Duration.ofMillis(50));
  }

  private static FinlightConfig config(String url) {
    return FinlightConfig.builder("test-key").wssUrl(url).timeout(Duration.ofSeconds(5)).build();
  }

  private static boolean isHandshake(String message) throws Exception {
    return !Json.mapper().readTree(message).has("action");
  }

  @Test
  void handshakeDedupAndPreempt() throws Exception {
    AtomicReference<String> apiKey = new AtomicReference<>();
    AtomicReference<String> clientVersion = new AtomicReference<>();
    AtomicReference<String> takeover = new AtomicReference<>();
    AtomicReference<JsonNode> handshake = new AtomicReference<>();

    try (WsTestServer server =
        WsTestServer.open(
            (conn, hs) -> {
              apiKey.set(hs.getFieldValue("x-api-key"));
              clientVersion.set(hs.getFieldValue("x-client-version"));
              takeover.set(hs.getFieldValue("x-takeover"));
            },
            (conn, message) -> {
              if (!isHandshake(message)) {
                return;
              }
              JsonNode node = Json.mapper().readTree(message);
              handshake.set(node);
              String nonce = node.path("clientNonce").asText();
              conn.send(
                  "{\"action\":\"admit\",\"leaseId\":\"lease-1\",\"clientNonce\":\""
                      + nonce
                      + "\"}");
              conn.send(WsTestServer.articleMessage("https://example.com/a"));
              conn.send(WsTestServer.articleMessage("https://example.com/a"));
              conn.send(WsTestServer.articleMessage("https://example.com/b"));
              conn.send("{\"action\":\"preempted\",\"reason\":\"test over\"}");
            })) {

      ArticleWebSocketClient client =
          new ArticleWebSocketClient(config(server.url()), fastOptions().takeover(true).build());
      List<Article> received = new CopyOnWriteArrayList<>();
      client
          .connectAsync(GetArticlesWebSocketParams.builder().query("nvidia").build(), received::add)
          .get(15, TimeUnit.SECONDS);

      assertEquals(
          List.of("https://example.com/a", "https://example.com/b"),
          received.stream().map(Article::link).toList());
      assertEquals("test-key", apiKey.get());
      assertTrue(
          clientVersion.get().startsWith("java/finlight-client@"),
          "unexpected client version: " + clientVersion.get());
      assertEquals("true", takeover.get());
      assertNotNull(handshake.get());
      assertEquals("nvidia", handshake.get().path("query").asText());
      assertEquals(36, handshake.get().path("clientNonce").asText().length());
      assertEquals(List.of(), server.errors);
    }
  }

  @Test
  void rawStreamDoesNotDedupAndUsesRawPath() throws Exception {
    try (WsTestServer server =
        WsTestServer.open(
            (conn, hs) -> {},
            (conn, message) -> {
              if (!isHandshake(message)) {
                return;
              }
              conn.send(WsTestServer.articleMessage("https://example.com/a"));
              conn.send(WsTestServer.articleMessage("https://example.com/a"));
              conn.send("{\"action\":\"preempted\",\"reason\":\"test over\"}");
            })) {

      RawArticleWebSocketClient client =
          new RawArticleWebSocketClient(config(server.url()), fastOptions().build());
      List<RawArticle> received = new CopyOnWriteArrayList<>();
      client
          .connectAsync(GetRawArticlesWebSocketParams.builder().build(), received::add)
          .get(15, TimeUnit.SECONDS);

      assertEquals(2, received.size(), "raw stream must not deduplicate");
      assertEquals(List.of("/raw"), server.resourcePaths);
      assertEquals(List.of(), server.errors);
    }
  }

  @Test
  void blockedCloseCodeStopsReconnectingAndThrows() throws Exception {
    try (WsTestServer server =
        WsTestServer.open(
            (conn, hs) -> {},
            (conn, message) -> {
              if (isHandshake(message)) {
                conn.close(1008, "blocked");
              }
            })) {

      ArticleWebSocketClient client =
          new ArticleWebSocketClient(config(server.url()), fastOptions().build());
      CompletableFuture<Void> future =
          client.connectAsync(GetArticlesWebSocketParams.builder().build(), article -> {});

      ExecutionException e =
          org.junit.jupiter.api.Assertions.assertThrows(
              ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS));
      assertInstanceOf(FinlightBlockedException.class, e.getCause());
    }
  }

  @Test
  void reconnectsAfterServerClose() throws Exception {
    AtomicReference<WsTestServer> serverRef = new AtomicReference<>();
    try (WsTestServer server =
        WsTestServer.open(
            (conn, hs) -> {},
            (conn, message) -> {
              if (!isHandshake(message)) {
                return;
              }
              if (connectionOf(serverRef, conn) == 1) {
                conn.send(WsTestServer.articleMessage("https://example.com/first"));
                conn.close(1000, "server restart");
              } else {
                conn.send(WsTestServer.articleMessage("https://example.com/second"));
                conn.send("{\"action\":\"preempted\",\"reason\":\"test over\"}");
              }
            })) {
      serverRef.set(server);

      ArticleWebSocketClient client =
          new ArticleWebSocketClient(config(server.url()), fastOptions().build());
      List<Article> received = new CopyOnWriteArrayList<>();
      client
          .connectAsync(GetArticlesWebSocketParams.builder().build(), received::add)
          .get(15, TimeUnit.SECONDS);

      assertEquals(
          List.of("https://example.com/first", "https://example.com/second"),
          received.stream().map(Article::link).toList());
      assertEquals(List.of(), server.errors);
    }
  }

  @Test
  void sendsApplicationPingAndHandlesPong() throws Exception {
    AtomicLong pingTime = new AtomicLong();
    try (WsTestServer server =
        WsTestServer.open(
            (conn, hs) -> {},
            (conn, message) -> {
              JsonNode node = Json.mapper().readTree(message);
              if ("ping".equals(node.path("action").asText())) {
                pingTime.set(node.path("t").asLong());
                conn.send("{\"action\":\"pong\",\"t\":" + node.path("t").asLong() + "}");
                conn.send("{\"action\":\"preempted\",\"reason\":\"test over\"}");
              }
            })) {

      ArticleWebSocketClient client =
          new ArticleWebSocketClient(
              config(server.url()), fastOptions().pingInterval(Duration.ofMillis(100)).build());
      client
          .connectAsync(GetArticlesWebSocketParams.builder().build(), article -> {})
          .get(15, TimeUnit.SECONDS);

      assertTrue(pingTime.get() > 0, "expected an application-level ping with a timestamp");
    }
  }

  @Test
  void adminKickDelaysReconnect() throws Exception {
    AtomicReference<WsTestServer> serverRef = new AtomicReference<>();
    List<Long> openTimes = new CopyOnWriteArrayList<>();
    try (WsTestServer server =
        WsTestServer.open(
            (conn, hs) -> openTimes.add(System.currentTimeMillis()),
            (conn, message) -> {
              if (!isHandshake(message)) {
                return;
              }
              if (connectionOf(serverRef, conn) == 1) {
                conn.send("{\"action\":\"admin_kick\",\"retryAfter\":400}");
              } else {
                conn.send("{\"action\":\"preempted\",\"reason\":\"test over\"}");
              }
            })) {
      serverRef.set(server);

      ArticleWebSocketClient client =
          new ArticleWebSocketClient(config(server.url()), fastOptions().build());
      client
          .connectAsync(GetArticlesWebSocketParams.builder().build(), article -> {})
          .get(15, TimeUnit.SECONDS);

      assertEquals(2, openTimes.size());
      long gap = openTimes.get(1) - openTimes.get(0);
      assertTrue(gap >= 300, "reconnect after admin_kick came too early: " + gap + "ms");
      assertEquals(List.of(), server.errors);
    }
  }

  @Test
  void stopEndsTheStream() throws Exception {
    try (WsTestServer server =
        WsTestServer.open(
            (conn, hs) -> {},
            (conn, message) -> {
              if (isHandshake(message)) {
                conn.send(WsTestServer.articleMessage("https://example.com/only"));
              }
            })) {

      ArticleWebSocketClient client =
          new ArticleWebSocketClient(config(server.url()), fastOptions().build());
      List<Article> received = new CopyOnWriteArrayList<>();
      CompletableFuture<Void> future =
          client.connectAsync(
              GetArticlesWebSocketParams.builder().build(),
              article -> {
                received.add(article);
                client.stop();
              });

      future.get(15, TimeUnit.SECONDS);
      assertEquals(1, received.size());
    }
  }

  // Helper to identify the 1-based connection index from within message handlers.
  private static int connectionOf(
      AtomicReference<WsTestServer> serverRef, org.java_websocket.WebSocket conn) {
    WsTestServer server = serverRef.get();
    Integer index = server == null ? null : server.connectionIndex.get(conn);
    return index == null ? -1 : index;
  }
}
