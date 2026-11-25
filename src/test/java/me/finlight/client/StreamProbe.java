package me.finlight.client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Runs a stream against the in-process server and records everything it can observe. */
final class StreamProbe implements StreamListener, StreamEngine.TestHooks {
  record Scheduled(long delayMs, ReconnectReason reason, long dueAt) {}

  final List<StreamEvent> items = new CopyOnWriteArrayList<>();
  final List<AdmittedInfo> admitted = new CopyOnWriteArrayList<>();
  final List<ReconnectInfo> reconnects = new CopyOnWriteArrayList<>();
  final List<FinlightException> errors = new CopyOnWriteArrayList<>();
  final List<QuotaWaitInfo> quotaWaits = new CopyOnWriteArrayList<>();
  final List<Scheduled> scheduled = new CopyOnWriteArrayList<>();
  final List<String> duplicates = new CopyOnWriteArrayList<>();
  final AtomicInteger frames = new AtomicInteger();
  final AtomicInteger pongs = new AtomicInteger();
  final AtomicInteger pingsSent = new AtomicInteger();
  final FakeClock clock = new FakeClock();
  volatile @Nullable FinlightException terminal;
  volatile boolean done;
  volatile @Nullable Consumer<StreamEvent> onEventHook;
  private final @Nullable String nonce;
  private final WsServer ws;
  FinlightClient client;
  FinlightStream stream;

  StreamProbe(WsServer ws, @Nullable String nonce) {
    this.ws = ws;
    this.nonce = nonce;
  }

  StreamProbe start(
      String apiKey,
      boolean raw,
      com.fasterxml.jackson.databind.node.ObjectNode params,
      StreamOptions options,
      Consumer<FinlightConfig.Builder> config) {
    FinlightConfig.Builder b =
        FinlightConfig.builder(apiKey).wssUrl(ws.url()).baseUrl("http://127.0.0.1:9").clock(clock);
    config.accept(b);
    client = new FinlightClient(b.build());
    stream = client.open(params, raw, options, this, this);
    return this;
  }

  // --- listener (dispatcher thread) ---

  @Override
  public void onEvent(StreamEvent event) {
    Consumer<StreamEvent> hook = onEventHook;
    if (hook != null) {
      hook.accept(event);
    }
    items.add(event);
  }

  @Override
  public void onAdmitted(AdmittedInfo info) {
    admitted.add(info);
  }

  @Override
  public void onReconnect(ReconnectInfo info) {
    reconnects.add(info);
  }

  @Override
  public void onError(FinlightException error) {
    errors.add(error);
  }

  @Override
  public void onQuotaWait(QuotaWaitInfo info) {
    quotaWaits.add(info);
  }

  @Override
  public void onClosed(@Nullable FinlightException error) {
    terminal = error;
    done = true;
  }

  // --- test hooks (loop thread) ---

  @Override
  public @Nullable String nonce() {
    return nonce;
  }

  @Override
  public void onFrame() {
    frames.incrementAndGet();
  }

  @Override
  public void onPong() {
    pongs.incrementAndGet();
  }

  @Override
  public void onPingSent() {
    pingsSent.incrementAndGet();
  }

  @Override
  public void onDuplicate(String id) {
    duplicates.add(id);
  }

  @Override
  public void onReconnectScheduled(long delayMs, ReconnectReason reason, long dueAt) {
    scheduled.add(new Scheduled(delayMs, reason, dueAt));
  }

  // --- driving ---

  /** Lets the stream react: the loop drains and, with auto-pong, every ping got its pong. */
  void settle() {
    try {
      stream.engine().awaitIdle();
      if (ws.autoPong) {
        waitFor(
            () -> pongs.get() >= pingsSent.get() || ws.openConnections() == 0 || done,
            "a pong for every ping");
        stream.engine().awaitIdle();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  void advance(long ms) {
    settle();
    clock.advance(ms, this::settle);
    settle();
  }

  void stop() throws InterruptedException {
    stream.close();
    if (!stream.awaitTermination(java.time.Duration.ofSeconds(10))) {
      throw new AssertionError("the stream did not end");
    }
    client.close();
  }

  List<StreamEvent> articleItems() {
    return items.stream().filter(i -> i.kind() != StreamEventKind.OTHER).toList();
  }

  static void waitFor(BooleanSupplier condition, String what) {
    long end = System.currentTimeMillis() + 5000;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > end) {
        throw new AssertionError("timed out waiting for " + what);
      }
      try {
        Thread.sleep(2);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }

  static <T> List<T> copy(List<T> list) {
    return Collections.unmodifiableList(List.copyOf(list));
  }
}
