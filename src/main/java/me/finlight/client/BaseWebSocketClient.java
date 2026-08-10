package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.finlight.client.internal.ClientVersion;
import me.finlight.client.internal.Json;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Shared implementation of the finlight streaming protocol: reconnect loop with exponential
 * backoff, application-level ping/pong with watchdog, proactive connection rotation before the
 * server-side lifetime cap, and optional duplicate suppression.
 *
 * <p>Use the concrete {@link ArticleWebSocketClient} and {@link RawArticleWebSocketClient} obtained
 * from {@link FinlightClient}.
 *
 * @param <TArticle> article type delivered by the stream
 * @param <TParams> stream filter type
 */
public abstract class BaseWebSocketClient<TArticle, TParams> {

  private static final int RECENT_ARTICLE_CACHE_SIZE = 10;
  private static final long WATCHDOG_INTERVAL_MILLIS = 5_000;
  private static final long CLOSE_ABORT_GRACE_MILLIS = 5_000;
  private static final long DIAL_RATE_LIMIT_BACKOFF_MILLIS = 60_000;
  private static final long ERROR_RATE_LIMIT_BACKOFF_MILLIS = 60_000;
  private static final long ERROR_BLOCKED_BACKOFF_MILLIS = 3_600_000;
  private static final long DEFAULT_ADMIN_KICK_RETRY_MILLIS = 900_000;

  /** Close codes of the finlight WebSocket protocol. */
  private static final int CLOSE_BLOCKED = 1008;

  private static final int CLOSE_PROACTIVE_ROTATION = 4000;
  private static final int CLOSE_RATE_LIMITED = 4001;
  private static final int CLOSE_USER_BLOCKED = 4002;
  private static final int CLOSE_ADMIN_KICK = 4003;

  private final FinlightConfig config;
  private final WebSocketOptions options;
  private final Class<TArticle> articleType;
  private final HttpClient httpClient;
  final Logger log;

  private final LinkedHashSet<String> recentArticles = new LinkedHashSet<>();
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile boolean stopRequested;
  private volatile CountDownLatch stopLatch = new CountDownLatch(1);
  private volatile @Nullable Conn current;
  private volatile @Nullable ScheduledExecutorService scheduler;
  private volatile long reconnectAtMillis;

  BaseWebSocketClient(
      FinlightConfig config, WebSocketOptions options, Class<TArticle> articleType, Logger log) {
    this.config = config;
    this.options = options;
    this.articleType = articleType;
    this.log = log;
    HttpClient custom = config.httpClient();
    this.httpClient =
        custom != null ? custom : HttpClient.newBuilder().connectTimeout(config.timeout()).build();
  }

  /** URL this client connects to. */
  abstract String webSocketUrl();

  /** Identifier used for duplicate suppression, or null to disable it. */
  abstract @Nullable String articleId(TArticle article);

  final FinlightConfig config() {
    return config;
  }

  /** The options this client was created with. */
  public final WebSocketOptions options() {
    return options;
  }

  /**
   * Connects to the stream and blocks, invoking {@code onArticle} for every received article.
   * Reconnects automatically (exponential backoff, honors server-mandated wait times) until {@link
   * #stop()} is called or the server preempts the connection in favor of a newer one.
   *
   * @throws FinlightBlockedException if the server permanently rejected the connection (close code
   *     1008)
   * @throws IllegalStateException if this client is already connected
   */
  public final void connect(TParams params, Consumer<TArticle> onArticle) {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("finlight: connect() is already running on this client");
    }
    stopRequested = false;
    stopLatch = new CountDownLatch(1);
    reconnectAtMillis = 0;
    ScheduledExecutorService localScheduler =
        Executors.newSingleThreadScheduledExecutor(
            task -> {
              Thread thread = new Thread(task, "finlight-ws-keepalive");
              thread.setDaemon(true);
              return thread;
            });
    scheduler = localScheduler;
    try {
      runReconnectLoop(params, onArticle);
    } finally {
      scheduler = null;
      localScheduler.shutdownNow();
      current = null;
      running.set(false);
    }
  }

  /**
   * Runs {@link #connect} on a new daemon thread and returns a future that completes when the
   * stream ends (normally after {@link #stop()}, exceptionally on {@link
   * FinlightBlockedException}).
   */
  public final CompletableFuture<Void> connectAsync(TParams params, Consumer<TArticle> onArticle) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                connect(params, onArticle);
                future.complete(null);
              } catch (Throwable t) {
                future.completeExceptionally(t);
              }
            },
            "finlight-ws-connect");
    thread.setDaemon(true);
    thread.start();
    return future;
  }

  /**
   * Stops the stream: closes the current connection and ends the reconnect loop. Safe to call from
   * any thread; {@link #connect} returns shortly after.
   */
  public final void stop() {
    stopRequested = true;
    Conn conn = current;
    if (conn != null) {
      conn.close(WebSocket.NORMAL_CLOSURE, "client stopped");
    }
    stopLatch.countDown();
  }

  private void runReconnectLoop(TParams params, Consumer<TArticle> onArticle) {
    long delayMillis = options.baseReconnectDelay().toMillis();
    long maxDelayMillis = options.maxReconnectDelay().toMillis();

    while (!stopRequested) {
      log.info("connecting to {}", webSocketUrl());
      ConnResult result = runConnection(params, onArticle);
      if (result == ConnResult.BLOCKED) {
        throw new FinlightBlockedException();
      }
      if (stopRequested) {
        return;
      }
      if (result == ConnResult.CONNECTED) {
        delayMillis = options.baseReconnectDelay().toMillis();
      }

      long now = System.currentTimeMillis();
      long waitMillis;
      if (reconnectAtMillis > now) {
        waitMillis = reconnectAtMillis - now;
        log.info("waiting {}ms until server-mandated reconnect time", waitMillis);
      } else {
        waitMillis = delayMillis;
        log.info("reconnecting in {}ms", waitMillis);
        delayMillis = Math.min(delayMillis * 2, maxDelayMillis);
      }
      try {
        if (stopLatch.await(waitMillis, TimeUnit.MILLISECONDS)) {
          return;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private ConnResult runConnection(TParams params, Consumer<TArticle> onArticle) {
    Conn conn = new Conn(onArticle);

    WebSocket ws;
    try {
      // The server reads these headers case-sensitively in exact lowercase.
      WebSocket.Builder builder =
          httpClient
              .newWebSocketBuilder()
              .header("x-api-key", config.apiKey())
              .header("x-client-version", ClientVersion.get())
              .connectTimeout(config.timeout());
      if (options.takeover()) {
        builder.header("x-takeover", "true");
      }
      ws = builder.buildAsync(URI.create(webSocketUrl()), conn.listener()).join();
    } catch (CompletionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      if (cause instanceof WebSocketHandshakeException handshake
          && handshake.getResponse().statusCode() == 429) {
        reconnectAtMillis = System.currentTimeMillis() + DIAL_RATE_LIMIT_BACKOFF_MILLIS;
        log.warn(
            "server rejected connection (429), backing off {}ms", DIAL_RATE_LIMIT_BACKOFF_MILLIS);
      } else {
        log.error("connection failed: {}", cause.toString());
      }
      return ConnResult.FAILED;
    }

    conn.open(ws);
    current = conn;
    log.info("connected");
    reconnectAtMillis = 0;

    conn.sendText(withClientNonce(params, conn.nonce));

    ScheduledExecutorService localScheduler = scheduler;
    if (localScheduler == null) {
      // stop() raced connect(); shut the fresh connection down.
      conn.close(WebSocket.NORMAL_CLOSURE, "client stopped");
      return ConnResult.CONNECTED;
    }
    long pingMillis = options.pingInterval().toMillis();
    Future<?> pingTask =
        localScheduler.scheduleAtFixedRate(
            conn::sendPing, pingMillis, pingMillis, TimeUnit.MILLISECONDS);
    Future<?> watchdogTask =
        localScheduler.scheduleAtFixedRate(
            conn::watchdog,
            WATCHDOG_INTERVAL_MILLIS,
            WATCHDOG_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS);
    Future<?> rotationTask =
        localScheduler.schedule(
            conn::rotate, options.connectionLifetime().toMillis(), TimeUnit.MILLISECONDS);

    CloseInfo close = conn.closed.join();

    pingTask.cancel(false);
    watchdogTask.cancel(false);
    rotationTask.cancel(false);
    current = null;

    log.info("connection closed: {} - {}", close.code(), close.reason());
    notifyClose(close);

    if (close.code() == CLOSE_BLOCKED) {
      log.warn("connection rejected by server (blocked)");
      return ConnResult.BLOCKED;
    }
    return ConnResult.CONNECTED;
  }

  private void notifyClose(CloseInfo close) {
    BiConsumer<Integer, String> onClose = options.onClose();
    if (onClose == null) {
      return;
    }
    try {
      onClose.accept(close.code(), close.reason());
    } catch (RuntimeException e) {
      log.error("onClose callback failed", e);
    }
  }

  private String withClientNonce(TParams params, String nonce) {
    ObjectNode node = Json.mapper().valueToTree(params);
    node.put("clientNonce", nonce);
    return node.toString();
  }

  private enum ConnResult {
    FAILED,
    CONNECTED,
    BLOCKED
  }

  record CloseInfo(int code, String reason) {}

  /** State and message handling of one established connection. */
  final class Conn {
    final CompletableFuture<CloseInfo> closed = new CompletableFuture<>();
    final String nonce = UUID.randomUUID().toString();
    private final Consumer<TArticle> onArticle;
    private volatile @Nullable WebSocket ws;
    private volatile long lastPongMillis = System.currentTimeMillis();
    private CompletableFuture<@Nullable Object> sendChain = CompletableFuture.completedFuture(null);

    Conn(Consumer<TArticle> onArticle) {
      this.onArticle = onArticle;
    }

    void open(WebSocket webSocket) {
      this.ws = webSocket;
      this.lastPongMillis = System.currentTimeMillis();
    }

    WebSocket.Listener listener() {
      return new WebSocket.Listener() {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
          webSocket.request(1);
        }

        @Override
        public @Nullable CompletionStage<?> onText(
            WebSocket webSocket, CharSequence data, boolean last) {
          buffer.append(data);
          if (last) {
            String message = buffer.toString();
            buffer.setLength(0);
            handleMessage(message);
          }
          webSocket.request(1);
          return null;
        }

        @Override
        public @Nullable CompletionStage<?> onClose(
            WebSocket webSocket, int statusCode, String reason) {
          closed.complete(new CloseInfo(statusCode, reason));
          return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
          log.error("connection error: {}", error.toString());
          closed.complete(new CloseInfo(-1, String.valueOf(error.getMessage())));
        }
      };
    }

    // Visible for tests: dispatches one server message.
    void handleMessage(String message) {
      JsonNode msg;
      try {
        msg = Json.mapper().readTree(message);
      } catch (Exception e) {
        log.error("cannot parse message: {}", e.toString());
        return;
      }
      String action = msg.path("action").asText("");
      long now = System.currentTimeMillis();

      switch (action) {
        case "pong" -> {
          long pingTime = msg.path("t").asLong(0);
          if (pingTime > 0) {
            log.debug("pong received (rtt={}ms)", now - pingTime);
          } else {
            log.debug("pong received");
          }
          lastPongMillis = now;
        }
        case "admit" -> {
          log.info("admitted (leaseId={})", msg.path("leaseId").asText(""));
          String serverNonce = msg.path("clientNonce").asText("");
          if (!serverNonce.isEmpty() && !serverNonce.equals(nonce)) {
            log.warn("nonce mismatch: expected {}, got {}", nonce, serverNonce);
          }
        }
        case "preempted" -> {
          log.warn(
              "connection preempted: {} (new lease: {})",
              msg.path("reason").asText("unknown"),
              msg.path("newLeaseId").asText(""));
          stopRequested = true;
          close(WebSocket.NORMAL_CLOSURE, "Preempted by server");
        }
        case "sendArticle" -> handleArticle(msg.path("data"));
        case "admin_kick" -> {
          long retryAfter = msg.path("retryAfter").asLong(DEFAULT_ADMIN_KICK_RETRY_MILLIS);
          reconnectAtMillis = now + retryAfter;
          log.warn("admin kick - retry after {}ms", retryAfter);
          close(CLOSE_ADMIN_KICK, "Admin kick");
        }
        case "error" -> {
          String errText = nodeToText(msg.get("data"));
          if (errText.isEmpty()) {
            errText = nodeToText(msg.get("error"));
          }
          log.error("server error: {}", errText);
          String lower = errText.toLowerCase(Locale.ROOT);
          if (lower.contains("limit")) {
            reconnectAtMillis = now + ERROR_RATE_LIMIT_BACKOFF_MILLIS;
            close(CLOSE_RATE_LIMITED, "Rate limited");
          } else if (lower.contains("blocked")) {
            reconnectAtMillis = now + ERROR_BLOCKED_BACKOFF_MILLIS;
            close(CLOSE_USER_BLOCKED, "User blocked");
          }
        }
        default -> log.warn("unknown message action: {}", action);
      }
    }

    private void handleArticle(JsonNode data) {
      TArticle article;
      try {
        article = Json.mapper().treeToValue(data, articleType);
      } catch (Exception e) {
        log.error("cannot parse article: {}", e.toString());
        return;
      }
      String id = articleId(article);
      if (id != null) {
        if (recentArticles.contains(id)) {
          log.debug("skipping duplicate article: {}", id);
          return;
        }
        recentArticles.add(id);
        if (recentArticles.size() > RECENT_ARTICLE_CACHE_SIZE) {
          Iterator<String> oldest = recentArticles.iterator();
          oldest.next();
          oldest.remove();
        }
      }
      try {
        onArticle.accept(article);
      } catch (RuntimeException e) {
        log.error("article callback failed", e);
      }
    }

    void sendPing() {
      long now = System.currentTimeMillis();
      log.debug("sending ping (t={})", now);
      sendText("{\"action\":\"ping\",\"t\":" + now + "}");
    }

    void watchdog() {
      if (System.currentTimeMillis() - lastPongMillis > options().pongTimeout().toMillis()) {
        log.warn("no pong received in time, forcing reconnect");
        abort("pong timeout");
      }
    }

    void rotate() {
      log.info("proactive rotation before server connection cap");
      close(CLOSE_PROACTIVE_ROTATION, "Proactive rotation");
    }

    void sendText(String text) {
      WebSocket socket = ws;
      if (socket != null) {
        chainSend(() -> socket.sendText(text, true));
      }
    }

    /**
     * Sends a close frame and aborts the connection if the server does not complete the close
     * handshake within a grace period.
     */
    void close(int code, String reason) {
      WebSocket socket = ws;
      if (socket == null) {
        closed.complete(new CloseInfo(code, reason));
        return;
      }
      chainSend(() -> socket.sendClose(code, reason));
      ScheduledExecutorService localScheduler = scheduler;
      if (localScheduler != null && !localScheduler.isShutdown()) {
        try {
          Future<?> unused =
              localScheduler.schedule(
                  () -> abort(reason), CLOSE_ABORT_GRACE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
          // scheduler already shut down; the reconnect loop has ended anyway
        }
      }
    }

    private void abort(String reason) {
      WebSocket socket = ws;
      if (socket != null) {
        socket.abort();
      }
      closed.complete(new CloseInfo(-1, reason));
    }

    private synchronized void chainSend(Supplier<CompletableFuture<WebSocket>> operation) {
      sendChain =
          sendChain
              .thenCompose(ignored -> operation.get().thenApply(w -> (Object) w))
              .exceptionally(
                  e -> {
                    log.debug("send failed: {}", e.toString());
                    return null;
                  });
    }

    private String nodeToText(@Nullable JsonNode node) {
      if (node == null || node.isNull() || node.isMissingNode()) {
        return "";
      }
      return node.isTextual() ? node.asText() : node.toString();
    }
  }
}
