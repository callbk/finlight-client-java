package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * One stream: dial, subscribe, keep-alive, watchdog, rotation, reconnect policy (R-WS-2), dedupe
 * and delivery. All state changes run on one daemon thread (the loop); user code runs on a second
 * daemon thread fed by a bounded queue, so control frames are handled before user code. The loop
 * never waits for the listener: when the queue is full, listener calls are parked and the stream
 * stops reading frames until the listener catches up, while pings and timers keep running.
 */
final class StreamEngine {
  static final String NO_WEBSOCKETS = "Your subscription does not include WebSockets";
  static final String QUOTA = "WebSocket quota exceeded";
  static final String TOO_MANY = "Too many concurrent connections";
  private static final int QUEUE_CAPACITY = 1024;

  /** How long a rotation waits for the old connection's close before it dials anyway. */
  private static final long ROTATION_CLOSE_WAIT_MS = 2_500;

  /** How long a terminal end waits for a stuck listener to take the parked events. */
  private static final long FLUSH_WAIT_MS = 30_000;

  private static final AtomicInteger IDS = new AtomicInteger();

  /** Hooks for tests; every method has an empty default. */
  interface TestHooks {
    default @Nullable String nonce() {
      return null;
    }

    default void onFrame() {}

    default void onPong() {}

    default void onPingSent() {}

    default void onDuplicate(String id) {}

    default void onReconnectScheduled(long delayMs, ReconnectReason reason, long dueAt) {}

    default void onDial() {}
  }

  private enum Delay {
    NOW,
    BACKOFF,
    LONG,
    QUOTA
  }

  /** What happens after a connection ends; {@code report} goes to onError unless dropped. */
  private record Planned(
      @Nullable FinlightException terminal,
      @Nullable ReconnectReason reason,
      Delay delay,
      @Nullable FinlightException report) {
    static Planned end(FinlightException error) {
      return new Planned(error, null, Delay.NOW, null);
    }

    static Planned reconnect(ReconnectReason reason, Delay delay) {
      return new Planned(null, reason, delay, null);
    }

    Planned reporting(FinlightException error) {
      return new Planned(terminal, reason, delay, error);
    }
  }

  private final FinlightConfig cfg;
  private final HttpClient http;
  private final ObjectNode params;
  private final boolean raw;
  private final boolean takeover;
  private final StreamListener listener;
  private final TestHooks hooks;
  private final String name;
  private final ScheduledThreadPoolExecutor loop;
  private final Dispatcher dispatcher;
  private final FinlightStream handle;
  private final CountDownLatch finished = new CountDownLatch(1);
  private final List<Runnable> endActions = new ArrayList<>();

  // Loop-thread state.
  private @Nullable Conn current;
  private @Nullable Conn rotatingFrom;
  private final List<Conn> open = new ArrayList<>();
  private int connections;
  private int failures;
  private int searchFailed;
  private int quotaWaits;
  private @Nullable FinlightException pendingQuotaError;
  private @Nullable Runnable cancelReconnect;
  private boolean ended;
  private final LinkedHashMap<String, Boolean> dedupe = new LinkedHashMap<>();
  private long lastSeenMs = Long.MIN_VALUE;
  private long lastBlockedEnd = Long.MIN_VALUE;
  private long blockedSince = Long.MIN_VALUE;

  /** Listener calls waiting for room in the dispatcher queue, in order. */
  private final ArrayDeque<Runnable> parked = new ArrayDeque<>();

  /** Connections that stopped reading because listener calls are parked. */
  private final List<Conn> paused = new ArrayList<>();

  private volatile boolean stopRequested;
  private volatile @Nullable String lastSeenAt;
  private volatile @Nullable FinlightException terminal;

  StreamEngine(
      FinlightConfig cfg,
      HttpClient http,
      ObjectNode params,
      boolean raw,
      boolean takeover,
      StreamListener listener) {
    this(cfg, http, params, raw, takeover, listener, new TestHooks() {});
  }

  StreamEngine(
      FinlightConfig cfg,
      HttpClient http,
      ObjectNode params,
      boolean raw,
      boolean takeover,
      StreamListener listener,
      TestHooks hooks) {
    this.cfg = cfg;
    this.http = http;
    this.params = params.deepCopy();
    this.raw = raw;
    this.takeover = takeover;
    this.listener = listener;
    this.hooks = hooks;
    this.name = "finlight-stream-" + IDS.incrementAndGet();
    this.loop =
        new ScheduledThreadPoolExecutor(
            1,
            r -> {
              Thread t = new Thread(r, name);
              t.setDaemon(true);
              return t;
            });
    loop.setRemoveOnCancelPolicy(true);
    loop.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    loop.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    this.dispatcher = new Dispatcher(name + "-events", () -> post(this::drainParked));
    this.handle = new FinlightStream(this);
  }

  /** Name prefix of the stream's threads. */
  String name() {
    return name;
  }

  FinlightStream handle() {
    return handle;
  }

  CountDownLatch finished() {
    return finished;
  }

  @Nullable FinlightException terminal() {
    return terminal;
  }

  @Nullable String lastSeenAt() {
    return lastSeenAt;
  }

  /** Runs after the stream ended (any thread). */
  synchronized void onEnd(Runnable action) {
    if (finished.getCount() == 0) {
      action.run();
    } else {
      endActions.add(action);
    }
  }

  /** Waits until the loop has run every task posted before (tests). */
  void awaitIdle() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    if (post(done::countDown)) {
      done.await(5, TimeUnit.SECONDS);
    }
  }

  /** True when the loop thread has stopped (tests). */
  boolean loopTerminated() {
    return loop.isTerminated();
  }

  void start() {
    dispatcher.start();
    post(() -> connect(null));
  }

  void stop() {
    stopRequested = true; // no listener call after close(), except onClosed
    if (!post(() -> finish(null))) {
      // The loop already ended.
      Log.debug(() -> name + " close: already ended");
    }
  }

  // --- timers --------------------------------------------------------------------------------

  private long now() {
    FinlightConfig.Clock c = cfg.clock();
    return c == null ? System.currentTimeMillis() : c.millis();
  }

  /** Schedules on the configured clock; the task runs on the loop. */
  private Runnable schedule(long delayMs, Runnable task) {
    FinlightConfig.Clock c = cfg.clock();
    if (c != null) {
      return c.schedule(Duration.ofMillis(delayMs), () -> post(task));
    }
    try {
      ScheduledFuture<?> f = loop.schedule(guard(task), delayMs, TimeUnit.MILLISECONDS);
      return () -> f.cancel(false);
    } catch (RejectedExecutionException e) {
      return () -> {};
    }
  }

  private boolean post(Runnable task) {
    try {
      loop.execute(guard(task));
      return true;
    } catch (RejectedExecutionException e) {
      return false;
    }
  }

  private Runnable guard(Runnable task) {
    return () -> {
      try {
        task.run();
      } catch (RuntimeException e) {
        Log.warn(name + ": internal stream error", e);
        if (!ended) {
          finish(new FinlightNetworkException("the stream failed: " + e.getMessage(), e));
        }
      }
    };
  }

  // --- connecting ----------------------------------------------------------------------------

  private void connect(@Nullable ReconnectReason reason) {
    if (ended) {
      return;
    }
    cancelReconnect = null;
    String nonce = hooks.nonce();
    Conn c = new Conn(nonce != null ? nonce : "c-" + UUID.randomUUID());
    if (reason != null && reason.equals(ReconnectReason.ROTATION) && current != null) {
      rotatingFrom = current;
    }
    current = c;
    open.add(c);
    String url = cfg.wssUrl() + (raw ? "/raw" : "");
    Log.debug(() -> name + ": dialing " + url);
    hooks.onDial();
    WebSocket.Builder b =
        http.newWebSocketBuilder()
            .connectTimeout(cfg.connectTimeout())
            // The gateway reads these names case-sensitively in lowercase (R-WS-3).
            .header("x-api-key", cfg.apiKey())
            .header("x-client-version", BuildInfo.VERSION)
            .header("User-Agent", Transport.USER_AGENT);
    if (takeover) {
      b.header("x-takeover", "true");
    }
    CompletableFuture<WebSocket> dial;
    try {
      dial = b.buildAsync(URI.create(url), c.listener());
    } catch (RuntimeException e) {
      dial = CompletableFuture.failedFuture(e);
    }
    CompletableFuture<?> unused =
        dial.whenComplete(
            (ws, err) -> {
              if (!post(() -> onDial(c, ws, err, reason)) && ws != null) {
                ws.abort(); // the stream ended while dialing
              }
            });
  }

  private void onDial(
      Conn c, @Nullable WebSocket ws, @Nullable Throwable err, @Nullable ReconnectReason reason) {
    if (ended || c.dropped || c != current) {
      if (ws != null) {
        ws.abort();
      }
      open.remove(c);
      return;
    }
    if (err != null || ws == null) {
      open.remove(c);
      current = null;
      Planned planned = classifyDialFailure(err);
      Conn old = rotatingFrom;
      rotatingFrom = null;
      if (old != null && !old.dropped && planned.terminal() == null) {
        // The rotation dial failed while the old connection still works. Typically the plan's
        // connection limit is in use (by this very connection): close it, wait for its close and
        // dial again at once. Not an error for the user and no long wait. Only refusals that end
        // the stream (401, no WebSockets, quota with quota reconnect off) end it here too.
        pendingQuotaError = null;
        Log.debug(
            () ->
                name
                    + ": the renewing dial failed ("
                    + planned.reason()
                    + "); closing the old connection first");
        rotateAfterClose(old);
        return;
      }
      if (old != null) {
        drop(old);
      }
      FinlightException toReport = planned.report();
      if (toReport != null) {
        report(toReport);
      }
      apply(planned);
      return;
    }
    c.ws = ws;
    connections++;
    if (connections > 1) {
      ReconnectInfo info =
          new ReconnectInfo(
              lastSeenAt, reason == null ? ReconnectReason.CLOSED : reason, connections);
      enqueue(user(() -> listener.onReconnect(info)));
    }
    ObjectNode subscribe = Json.object();
    subscribe.put("payloadVersion", FinlightClient.PAYLOAD_VERSION);
    subscribe.setAll(params);
    subscribe.put("clientNonce", c.nonce);
    c.send(Json.write(subscribe));
    c.lastPong = now();
    armWatchdog(c);
    c.cancelPing = schedule(cfg.pingInterval().toMillis(), () -> ping(c));
    c.cancelRotation = schedule(cfg.rotationInterval().toMillis(), () -> rotate(c));
    ws.request(1);
  }

  /**
   * Break-before-make for a rotation that cannot dial a second connection: closes {@code old}, then
   * dials with reason rotation once its close arrived (or after a short wait).
   */
  private void rotateAfterClose(Conn old) {
    boolean[] done = {false};
    Runnable[] fallback = {null};
    Runnable redial =
        () -> {
          if (done[0]) {
            return;
          }
          done[0] = true;
          Runnable f = fallback[0];
          if (f != null) {
            f.run();
          }
          scheduleReconnect(ReconnectReason.ROTATION, Delay.NOW);
        };
    old.afterClose = redial;
    drop(old);
    if (!done[0]) {
      fallback[0] = schedule(ROTATION_CLOSE_WAIT_MS, redial);
      cancelReconnect = fallback[0];
    }
  }

  private Planned classifyDialFailure(@Nullable Throwable err) {
    Throwable cause = err;
    while ((cause instanceof CompletionException || cause instanceof ExecutionException)
        && cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (cause instanceof WebSocketHandshakeException h) {
      int status = h.getResponse().statusCode();
      Object body = h.getResponse().body();
      return classifyRefusal(status, body instanceof String s ? s : null);
    }
    FinlightNetworkException e =
        cause instanceof HttpTimeoutException
            ? new FinlightTimeoutException("the stream dial timed out", cause)
            : new FinlightNetworkException(
                "could not connect to the stream endpoint: "
                    + (cause == null ? "unknown" : cause.getClass().getSimpleName()),
                cause);
    return Planned.reconnect(ReconnectReason.DIAL_FAILED, Delay.BACKOFF).reporting(e);
  }

  /** R-WS-2 for a refused dial. 403 bodies are compared by their start. */
  private Planned classifyRefusal(int status, @Nullable String body) {
    String message = null;
    if (body != null && !body.isBlank()) {
      try {
        JsonNode j = Json.parse(body);
        if (j.isObject()) {
          message = Problems.text(j, "message");
          if (message == null) {
            message = Problems.text(j, "error");
          }
        }
      } catch (Exception e) {
        message = body.trim();
      }
    }
    String text = message == null ? "" : message;
    if (status == 401) {
      return Planned.end(dialError(status, message, body, null));
    }
    if (status == 403) {
      if (text.startsWith(NO_WEBSOCKETS)) {
        return Planned.end(dialError(status, message, body, null));
      }
      if (text.startsWith(QUOTA)) {
        return quotaPlan(dialError(status, message, body, null));
      }
      if (text.startsWith(TOO_MANY)) {
        return Planned.reconnect(ReconnectReason.CONNECTION_LIMIT, Delay.LONG)
            .reporting(
                dialError(
                    status,
                    message,
                    body,
                    "The connection limit is shared by enriched, raw and older SDK streams; the"
                        + " SDK retries after a long wait. Use StreamOptions.takeover(true) to"
                        + " replace your stalest connection."));
      }
      return Planned.reconnect(ReconnectReason.DIAL_REFUSED, Delay.LONG)
          .reporting(dialError(status, message, body, null));
    }
    if (status == 429) {
      return Planned.reconnect(ReconnectReason.RATE_LIMITED, Delay.LONG)
          .reporting(dialError(status, message, body, null));
    }
    if (status >= 500) {
      Log.debug(() -> name + ": dial refused with " + status + ", retrying");
      return Planned.reconnect(ReconnectReason.DIAL_REFUSED, Delay.BACKOFF);
    }
    return Planned.reconnect(ReconnectReason.DIAL_REFUSED, Delay.BACKOFF)
        .reporting(dialError(status, message, body, null));
  }

  private static FinlightApiException dialError(
      int status, @Nullable String message, @Nullable String body, @Nullable String hint) {
    ApiError e = new ApiError();
    e.status = status;
    e.apiKind =
        status == 401
            ? ApiErrorKind.UNAUTHORIZED
            : status == 403
                ? ApiErrorKind.FORBIDDEN
                : status == 429
                    ? ApiErrorKind.RATE_LIMITED
                    : status >= 500 ? ApiErrorKind.INTERNAL_ERROR : ApiErrorKind.OTHER;
    e.title = "WebSocket connection refused";
    e.detail = message;
    e.raw = body == null || body.isEmpty() ? null : body;
    e.hint = hint;
    if (e.apiKind == ApiErrorKind.RATE_LIMITED) {
      e.limitType = RateLimitType.UNKNOWN;
    }
    return e.build();
  }

  private Planned quotaPlan(FinlightException error) {
    if (!cfg.quotaReconnect()) {
      return Planned.end(error);
    }
    pendingQuotaError = error;
    return Planned.reconnect(ReconnectReason.QUOTA_EXCEEDED, Delay.QUOTA);
  }

  // --- frames --------------------------------------------------------------------------------

  private void onMessage(Conn c, String text) {
    if (ended || c.dropped) {
      return;
    }
    try {
      JsonNode json;
      try {
        json = Json.parse(text);
      } catch (Exception e) {
        Log.debug(() -> name + ": ignored a frame that is not JSON");
        return;
      }
      handle(c, Frames.decode(json), json);
    } finally {
      hooks.onFrame();
      WebSocket ws = c.ws;
      if (ws != null && !c.dropped && !ended) {
        if (parked.isEmpty()) {
          ws.request(1);
        } else if (!paused.contains(c)) {
          // The listener is behind: read nothing more until it caught up (back-pressure).
          paused.add(c);
        }
      }
    }
  }

  private void handle(Conn c, Frames.Frame frame, JsonNode json) {
    if (frame instanceof Frames.Pong) {
      // Only a pong or an article proves the connection healthy and restarts the backoff.
      failures = 0;
      c.lastPong = now();
      armWatchdog(c);
      hooks.onPong();
    } else if (frame instanceof Frames.Admitted a) {
      // Admitted comes before the subscribe is checked and searched: it does not reset the backoff.
      quotaWaits = 0;
      if (c == current && rotatingFrom != null) {
        Conn old = rotatingFrom;
        rotatingFrom = null;
        drop(old);
      }
      AdmittedInfo info = new AdmittedInfo(a.leaseId(), a.serverNow(), a.clientNonce());
      enqueue(user(() -> listener.onAdmitted(info)));
    } else if (frame instanceof Frames.Preempted p) {
      PreemptedException error = new PreemptedException(p.reason(), p.newLeaseId(), p.timestamp());
      if (p.reason().equals(PreemptReason.QUOTA_EXCEEDED)) {
        Planned planned = quotaPlan(error);
        if (planned.terminal() == null) {
          dropAndApply(c, planned);
          return;
        }
      }
      finish(error);
    } else if (frame instanceof Frames.ErrorFrame e) {
      handleError(c, e);
    } else if (frame instanceof Frames.ArticleFrame a) {
      handleArticle(a, json);
    } else if (frame instanceof Frames.OtherFrame o) {
      Log.debug(() -> name + ": unknown event " + o.event());
      StreamEvent.Other item = new StreamEvent.Other(o.event(), Json.write(json));
      enqueue(user(() -> listener.onEvent(item)));
    } else if (frame instanceof Frames.Ignored i) {
      Log.debug(() -> name + ": ignored a frame (" + i.reason() + ")");
    }
  }

  private void handleError(Conn c, Frames.ErrorFrame f) {
    String code = f.code();
    FinlightApiException error = streamError(f);
    if (ConnectionErrorCodeValues.VALIDATION_FAILED.equals(code)) {
      finish(error);
    } else if (ConnectionErrorCodeValues.HANDSHAKE_FAILED.equals(code)) {
      Log.warn(name + ": stream handshake failed; reconnecting");
      dropAndApply(c, Planned.reconnect(ReconnectReason.HANDSHAKE_FAILED, Delay.BACKOFF));
    } else if (ConnectionErrorCodeValues.SEARCH_FAILED.equals(code)) {
      searchFailed++;
      if (searchFailed >= cfg.searchFailedThreshold()) {
        searchFailed = 0;
        report(error);
      } else {
        Log.warn(name + ": the initial stream search failed; reconnecting");
      }
      dropAndApply(c, Planned.reconnect(ReconnectReason.SEARCH_FAILED, Delay.BACKOFF));
    } else {
      // PING_ON_DEFAULT_ROUTE, INTERNAL_ERROR, unknown codes and v2 error frames: report only.
      report(error);
    }
  }

  private static FinlightApiException streamError(Frames.ErrorFrame f) {
    ApiError e = new ApiError();
    String code = f.code();
    e.code = code == null ? null : ProblemCode.of(code);
    e.apiKind = ApiError.kindOf(e.code);
    e.status =
        e.apiKind == ApiErrorKind.VALIDATION_FAILED
            ? 400
            : e.apiKind == ApiErrorKind.INTERNAL_ERROR ? 500 : 0;
    e.title = "Stream error";
    e.detail = f.message().isEmpty() ? null : f.message();
    e.errors = new ArrayList<>(f.details());
    return e.build();
  }

  private void handleArticle(Frames.ArticleFrame f, JsonNode json) {
    searchFailed = 0;
    failures = 0;
    Article article;
    try {
      article = Article.read(f.data(), "data");
    } catch (Wire.Failure e) {
      report(
          new FinlightDecodeException(
              "skipped a stream article that could not be read: " + e.getMessage(),
              e.path,
              null,
              e));
      return;
    }
    String key = f.event().value() + "\n" + article.id() + "\n" + sha256(Json.canonical(f.data()));
    if (dedupe.containsKey(key)) {
      dedupe.remove(key);
      dedupe.put(key, Boolean.TRUE);
      Log.debug(() -> name + ": dropped a duplicate " + f.event() + " for " + article.id());
      hooks.onDuplicate(article.id());
      return;
    }
    dedupe.put(key, Boolean.TRUE);
    while (dedupe.size() > cfg.dedupeCacheSize()) {
      dedupe.remove(dedupe.keySet().iterator().next());
    }
    String seen =
        raw
            ? article.firstSeenAt()
            : article.availableAt() != null ? article.availableAt() : article.firstSeenAt();
    Instant t = Times.parse(seen);
    if (t != null && t.toEpochMilli() >= lastSeenMs) {
      lastSeenMs = t.toEpochMilli();
      lastSeenAt = seen;
    }
    String rawFrame = Json.write(json);
    StreamEvent item =
        f.kind() == StreamEventKind.CREATED
            ? new StreamEvent.Created(f.event(), article, rawFrame)
            : new StreamEvent.Updated(f.event(), article, rawFrame);
    enqueue(user(() -> listener.onEvent(item)));
  }

  private static String sha256(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // --- keep-alive ----------------------------------------------------------------------------

  private void ping(Conn c) {
    if (ended || c.dropped || c.ws == null) {
      return;
    }
    c.send("{\"action\":\"ping\",\"t\":" + now() + "}");
    hooks.onPingSent();
    c.cancelPing = schedule(cfg.pingInterval().toMillis(), () -> ping(c));
  }

  private void armWatchdog(Conn c) {
    Runnable old = c.cancelWatchdog;
    if (old != null) {
      old.run();
    }
    c.cancelWatchdog = schedule(cfg.watchdogTimeout().toMillis(), () -> watchdog(c));
  }

  private void watchdog(Conn c) {
    if (ended || c.dropped || c != current) {
      return;
    }
    if (lastBlockedEnd > c.lastPong || !parked.isEmpty()) {
      // We stopped reading because the listener was slow; that is not a dead connection.
      c.lastPong = Math.max(c.lastPong, lastBlockedEnd);
      armWatchdog(c);
      return;
    }
    Log.warn(name + ": no pong for " + cfg.watchdogTimeout().toSeconds() + " s; reconnecting");
    dropAndApply(c, Planned.reconnect(ReconnectReason.WATCHDOG, Delay.BACKOFF));
  }

  private void rotate(Conn c) {
    if (ended || c.dropped || c != current) {
      return;
    }
    Log.debug(() -> name + ": renewing the connection before the 2 h gateway limit");
    hooks.onReconnectScheduled(0, ReconnectReason.ROTATION, now());
    connect(ReconnectReason.ROTATION);
  }

  // --- closing -------------------------------------------------------------------------------

  private void onClosed(Conn c, int code) {
    if (c.dropped) {
      Runnable after = c.afterClose;
      c.afterClose = null;
      if (after != null && !ended) {
        after.run();
      }
      return;
    }
    clearTimers(c);
    open.remove(c);
    c.dropped = true;
    if (ended) {
      return;
    }
    if (c == rotatingFrom) {
      rotatingFrom = null;
      return;
    }
    if (c != current) {
      return;
    }
    current = null;
    Planned planned;
    if (code == 1008) {
      ApiError e = new ApiError();
      e.status = 1008;
      e.title = "Policy violation";
      e.detail = "the server closed the stream with code 1008";
      planned = Planned.end(e.build());
    } else {
      Log.debug(() -> name + ": connection closed (" + code + "); reconnecting");
      planned = Planned.reconnect(ReconnectReason.CLOSED, Delay.BACKOFF);
    }
    Conn old = rotatingFrom;
    if (old != null) {
      rotatingFrom = null;
      drop(old);
      if (planned.terminal() == null) {
        planned = Planned.reconnect(ReconnectReason.ROTATION, Delay.NOW);
      }
    }
    apply(planned);
  }

  /** Closes a connection without waiting for the close handshake; its events are ignored. */
  private void drop(Conn c) {
    if (c.dropped) {
      return;
    }
    c.dropped = true;
    clearTimers(c);
    open.remove(c);
    if (c == current) {
      current = null;
    }
    c.closeQuietly();
  }

  private void dropAndApply(Conn c, Planned planned) {
    boolean wasCurrent = c == current;
    drop(c);
    if (wasCurrent) {
      apply(planned);
    }
  }

  private void clearTimers(Conn c) {
    for (Runnable r : new Runnable[] {c.cancelPing, c.cancelWatchdog, c.cancelRotation}) {
      if (r != null) {
        r.run();
      }
    }
    c.cancelPing = null;
    c.cancelWatchdog = null;
    c.cancelRotation = null;
  }

  private void apply(Planned planned) {
    FinlightException error = planned.terminal();
    if (error != null) {
      finish(error);
      return;
    }
    ReconnectReason reason = planned.reason();
    scheduleReconnect(reason == null ? ReconnectReason.CLOSED : reason, planned.delay());
  }

  private void scheduleReconnect(ReconnectReason reason, Delay kind) {
    if (ended) {
      return;
    }
    long delay;
    switch (kind) {
      case BACKOFF:
        {
          long base = cfg.reconnectBaseDelay().toMillis();
          long capped =
              Math.min(cfg.reconnectMaxDelay().toMillis(), base * (1L << Math.min(failures, 20)));
          delay =
              Math.max(
                  1, Math.round(capped * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5)));
          break;
        }
      case LONG:
        {
          long base = cfg.longBackoff().toMillis();
          delay = base + Math.round(base * 0.2 * ThreadLocalRandom.current().nextDouble());
          break;
        }
      case QUOTA:
        {
          delay = cfg.quotaReconnectInterval().toMillis();
          quotaWaits++;
          FinlightException error = pendingQuotaError;
          pendingQuotaError = null;
          if (error == null) {
            error = dialError(403, QUOTA, null, null);
          }
          Log.warn(
              name
                  + ": WebSocket quota exhausted ("
                  + error.getMessage()
                  + "); attempt "
                  + quotaWaits
                  + ", next try in "
                  + delay
                  + " ms");
          QuotaWaitInfo info = new QuotaWaitInfo(error, Duration.ofMillis(delay), quotaWaits);
          enqueue(user(() -> listener.onQuotaWait(info)));
          break;
        }
      default:
        delay = 0;
    }
    failures++;
    long d = delay;
    Log.debug(() -> name + ": reconnect (" + reason + ") in " + d + " ms");
    hooks.onReconnectScheduled(delay, reason, now() + delay);
    if (delay == 0) {
      connect(reason);
    } else {
      cancelReconnect = schedule(delay, () -> connect(reason));
    }
  }

  private void report(FinlightException error) {
    Log.debug(() -> name + ": reported " + error.getMessage());
    enqueue(user(() -> listener.onError(error)));
  }

  /** Ends the stream: closes every socket, cancels every timer and stops both threads. */
  private void finish(@Nullable FinlightException error) {
    if (ended) {
      return;
    }
    ended = true;
    terminal = error;
    if (error != null) {
      Log.debug(() -> name + ": ended with " + error.kind() + ": " + error.getMessage());
    }
    Runnable r = cancelReconnect;
    if (r != null) {
      r.run();
    }
    for (Conn c : List.copyOf(open)) {
      drop(c);
    }
    current = null;
    rotatingFrom = null;
    paused.clear();
    if (stopRequested) {
      parked.clear(); // skipped after close() anyway
    } else {
      // Events read before a terminal end still reach the listener, in order, before onClosed.
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FLUSH_WAIT_MS);
      Runnable next;
      while ((next = parked.poll()) != null) {
        if (!dispatcher.offerUntil(next, deadline)) {
          Log.warn(name + ": the listener did not take " + (parked.size() + 1) + " events");
          parked.clear();
        }
      }
    }
    dispatcher.finish(
        () -> listener.onClosed(error),
        () -> {
          List<Runnable> actions;
          synchronized (this) {
            finished.countDown();
            actions = List.copyOf(endActions);
            endActions.clear();
          }
          actions.forEach(Runnable::run);
        });
    loop.shutdown();
  }

  /** A listener call that is skipped once close() was called. */
  private Runnable user(Runnable call) {
    return () -> {
      if (!stopRequested) {
        call.run();
      }
    };
  }

  /**
   * Queues a listener call without ever blocking the loop. When the dispatcher queue is full the
   * call is parked; parked calls keep their order and move on as soon as the listener takes one.
   */
  private void enqueue(Runnable task) {
    if (ended) {
      return;
    }
    parked.add(task);
    drainParked();
  }

  /** Moves parked calls into the dispatcher queue; resumes reading once nothing is parked. */
  private void drainParked() {
    if (parked.isEmpty()) {
      return;
    }
    // Set before trying: a take after a failed offer then posts the next drain.
    dispatcher.wantSpace();
    Runnable next;
    while ((next = parked.peek()) != null) {
      if (!dispatcher.offerNow(next)) {
        if (blockedSince == Long.MIN_VALUE) {
          blockedSince = now();
        }
        return;
      }
      parked.poll();
    }
    if (blockedSince != Long.MIN_VALUE) {
      long slow = now() - blockedSince;
      blockedSince = Long.MIN_VALUE;
      lastBlockedEnd = now();
      Log.debug(() -> name + ": the listener was slow for " + slow + " ms");
    }
    if (!paused.isEmpty()) {
      List<Conn> resume = List.copyOf(paused);
      paused.clear();
      for (Conn c : resume) {
        WebSocket ws = c.ws;
        if (ws != null && !c.dropped && !ended) {
          ws.request(1);
        }
      }
    }
  }

  // --- one connection ------------------------------------------------------------------------

  private final class Conn {
    final String nonce;
    volatile @Nullable WebSocket ws;
    volatile boolean dropped;
    long lastPong;
    @Nullable Runnable cancelPing;
    @Nullable Runnable cancelWatchdog;
    @Nullable Runnable cancelRotation;

    /** Runs on the loop when this dropped connection's close arrives. */
    @Nullable Runnable afterClose;

    private final StringBuilder buffer = new StringBuilder();
    private long bufferBytes;
    private CompletableFuture<?> sends = CompletableFuture.completedFuture(null);

    Conn(String nonce) {
      this.nonce = nonce;
    }

    synchronized void send(String text) {
      WebSocket socket = ws;
      if (socket == null) {
        return;
      }
      sends =
          sends
              .handle((x, e) -> null)
              .thenCompose(x -> socket.sendText(text, true))
              .exceptionally(
                  e -> {
                    Log.debug(() -> name + ": send failed: " + e.getClass().getSimpleName());
                    return null;
                  });
    }

    synchronized void closeQuietly() {
      WebSocket socket = ws;
      if (socket == null) {
        return;
      }
      sends =
          sends
              .handle((x, e) -> null)
              .thenCompose(x -> socket.sendClose(WebSocket.NORMAL_CLOSURE, ""))
              .exceptionally(e -> null);
      // Do not wait for the server's close frame: abort the socket shortly after.
      CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(socket::abort);
    }

    WebSocket.Listener listener() {
      return new WebSocket.Listener() {
        @Override
        public void onOpen(WebSocket webSocket) {
          // Frames are requested one by one after the subscribe went out.
        }

        @Override
        public @Nullable CompletionStage<?> onText(
            WebSocket webSocket, CharSequence data, boolean last) {
          buffer.append(data);
          bufferBytes += utf8Length(data);
          if (bufferBytes > cfg.maxMessageBytes()) {
            buffer.setLength(0);
            bufferBytes = 0;
            Log.warn(name + ": a stream message exceeded maxMessageBytes; reconnecting");
            webSocket.abort();
            return null;
          }
          if (!last) {
            webSocket.request(1);
            return null;
          }
          String message = buffer.toString();
          buffer.setLength(0);
          bufferBytes = 0;
          if (!post(() -> onMessage(Conn.this, message))) {
            webSocket.abort();
          }
          return null;
        }

        @Override
        public @Nullable CompletionStage<?> onBinary(
            WebSocket webSocket, ByteBuffer data, boolean last) {
          webSocket.request(1);
          return null;
        }

        @Override
        public @Nullable CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
          webSocket.request(1);
          return null;
        }

        @Override
        public @Nullable CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
          webSocket.request(1);
          return null;
        }

        @Override
        public @Nullable CompletionStage<?> onClose(
            WebSocket webSocket, int statusCode, String reason) {
          post(() -> onClosed(Conn.this, statusCode));
          return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
          Log.debug(() -> name + ": socket error " + error.getClass().getSimpleName());
          post(() -> onClosed(Conn.this, -1));
        }
      };
    }
  }

  /** The UTF-8 size of {@code s} without encoding it. */
  static long utf8Length(CharSequence s) {
    long n = 0;
    int len = s.length();
    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);
      if (ch < 0x80) {
        n++;
      } else if (ch < 0x800) {
        n += 2;
      } else if (Character.isHighSurrogate(ch)
          && i + 1 < len
          && Character.isLowSurrogate(s.charAt(i + 1))) {
        n += 4;
        i++;
      } else {
        n += 3;
      }
    }
    return n;
  }

  // --- user code -----------------------------------------------------------------------------

  /** One daemon thread that runs listener code from a bounded queue (R-WS-6). */
  private static final class Dispatcher {
    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final Thread thread;
    private final Runnable onSpace;
    private final AtomicBoolean spaceWanted = new AtomicBoolean();
    private volatile boolean finishing;

    /**
     * {@code onSpace} runs on the dispatcher thread after a take, when {@link #wantSpace} was set.
     */
    Dispatcher(String threadName, Runnable onSpace) {
      this.thread = new Thread(this::run, threadName);
      this.thread.setDaemon(true);
      this.onSpace = onSpace;
    }

    void start() {
      thread.start();
    }

    void wantSpace() {
      spaceWanted.set(true);
    }

    boolean offerNow(Runnable task) {
      return !finishing && queue.offer(task);
    }

    /** Queues a task, waiting while the queue is full but not past {@code deadlineNanos}. */
    boolean offerUntil(Runnable task, long deadlineNanos) {
      try {
        while (!finishing) {
          if (queue.offer(task, 100, TimeUnit.MILLISECONDS)) {
            return true;
          }
          if (System.nanoTime() - deadlineNanos > 0) {
            return false;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return false;
    }

    /** Queues the last task and the end action; nothing is accepted after. */
    void finish(Runnable last, Runnable after) {
      Runnable end =
          () -> {
            safe(last);
            after.run();
            throw new Stop();
          };
      finishing = true;
      boolean queued = false;
      try {
        queued = queue.offer(end, 30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (!queued) {
        queue.clear();
        queue.add(end);
      }
    }

    private void run() {
      while (true) {
        Runnable task;
        try {
          task = queue.take();
        } catch (InterruptedException e) {
          return;
        }
        if (spaceWanted.compareAndSet(true, false)) {
          onSpace.run();
        }
        try {
          safe(task);
        } catch (Stop s) {
          return;
        }
      }
    }

    /** Runs user code; whatever it throws is logged, except a VM error (R-WS-6). */
    private static void safe(Runnable task) {
      try {
        task.run();
      } catch (Stop s) {
        throw s;
      } catch (VirtualMachineError e) {
        throw e;
      } catch (Throwable e) {
        Log.warn("a stream listener threw; the stream goes on", e);
      }
    }

    /** Ends the dispatcher thread. */
    private static final class Stop extends RuntimeException {
      private static final long serialVersionUID = 1L;

      Stop() {
        super(null, null, false, false);
      }
    }
  }
}
