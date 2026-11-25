package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** R-RETRY-1, R-CONFIG-1 and R-LOG-1. */
class RulesRetryConfigLogTest {
  private static final String BURST =
      "{\"type\":\"https://docs.finlight.me/errors/rate-limited\",\"status\":429,\"code\":\"RATE_LIMITED\","
          + "\"detail\":\"Throttling Exception: Exceeded rate limit of 5 requests per 10 seconds.\",\"limitType\":\"burst\"}";

  /** A clock whose sleep really blocks, so an interrupt can end it. */
  static final class BlockingClock implements FinlightConfig.Clock {
    final List<Duration> sleeps = new ArrayList<>();

    @Override
    public long millis() {
      return System.currentTimeMillis();
    }

    @Override
    public void sleep(Duration d) throws InterruptedException {
      sleeps.add(d);
      Thread.sleep(d.toMillis());
    }

    @Override
    public Runnable schedule(Duration delay, Runnable task) {
      return () -> {};
    }
  }

  @Test
  void cancellingDuringTheBurstWaitEndsAtOnce() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      BlockingClock clock = new BlockingClock();
      FinlightClient client =
          new FinlightClient(
              FinlightConfig.builder(Kit.API_KEY).baseUrl(mock.url()).clock(clock).build());
      mock.enqueue(429, "application/problem+json", BURST);
      AtomicReference<Throwable> thrown = new AtomicReference<>();
      AtomicReference<Boolean> interrupted = new AtomicReference<>();
      Thread t =
          new Thread(
              () -> {
                try {
                  client.articles().search();
                } catch (Throwable e) {
                  thrown.set(e);
                  interrupted.set(Thread.currentThread().isInterrupted());
                }
              });
      t.start();
      StreamProbe.waitFor(() -> !clock.sleeps.isEmpty(), "the burst wait");
      long start = System.nanoTime();
      t.interrupt();
      t.join(2000);
      assertFalse(t.isAlive());
      assertTrue((System.nanoTime() - start) / 1_000_000 < 1000, "the wait ended at once");
      CancellationException c = assertInstanceOf(CancellationException.class, thrown.get());
      assertInstanceOf(InterruptedException.class, c.getCause());
      assertTrue(interrupted.get(), "the interrupt flag is restored");
      assertEquals(1, mock.requests.size(), "a cancelled call is never retried");
      assertEquals(Duration.ofSeconds(10), clock.sleeps.get(0), "10 s without Retry-After");
      client.close();
    }
  }

  @Test
  void totalTimeoutCapsRetriesAndWaits() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient client =
          Support.client(mock, clock, b -> b.totalTimeout(Duration.ofSeconds(5)));
      mock.enqueue(429, "application/problem+json", BURST);
      RateLimitedException e =
          assertThrows(RateLimitedException.class, () -> client.articles().search());
      assertEquals(RateLimitType.BURST, e.limitType());
      assertEquals(1, mock.requests.size(), "a 10 s wait does not fit into 5 s");
      assertTrue(clock.sleeps.isEmpty());
      client.close();
    }
  }

  @Test
  void networkErrorsAreRetriedAndWrapped() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = s.getLocalPort();
    }
    FakeClock clock = new FakeClock();
    FinlightClient client =
        new FinlightClient(
            FinlightConfig.builder(Kit.API_KEY)
                .baseUrl("http://127.0.0.1:" + port)
                .clock(clock)
                .build());
    FinlightNetworkException e =
        assertThrows(FinlightNetworkException.class, () -> client.sources().list());
    assertEquals(FinlightErrorKind.NETWORK, e.kind());
    assertInstanceOf(IOException.class, e.getCause(), "the transport error is the cause");
    assertEquals(3, clock.sleeps.size(), "3 retries with backoff");
    client.close();
  }

  @Test
  void attemptTimeoutIsATimeoutError() throws Exception {
    try (ServerSocket silent = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
      List<java.net.Socket> held = new java.util.concurrent.CopyOnWriteArrayList<>();
      Thread acceptor =
          new Thread(
              () -> {
                try {
                  while (true) {
                    held.add(silent.accept()); // accept and never answer
                  }
                } catch (IOException e) {
                  // closed
                }
              });
      acceptor.setDaemon(true);
      acceptor.start();
      FakeClock clock = new FakeClock();
      FinlightClient client =
          new FinlightClient(
              FinlightConfig.builder(Kit.API_KEY)
                  .baseUrl("http://127.0.0.1:" + silent.getLocalPort())
                  .timeout(Duration.ofMillis(200))
                  .maxRetries(1)
                  .clock(clock)
                  .build());
      FinlightTimeoutException e =
          assertThrows(FinlightTimeoutException.class, () -> client.sources().list());
      assertInstanceOf(FinlightNetworkException.class, e);
      assertEquals(FinlightErrorKind.TIMEOUT, e.kind());
      assertEquals(1, clock.sleeps.size(), "retried once");
      client.close();
    }
  }

  @Test
  void urlsWithTrailingSlashesAndIdentityHeaders() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client =
          new FinlightClient(
              FinlightConfig.builder(Kit.API_KEY)
                  .baseUrl(mock.url() + "//")
                  .clock(new FakeClock())
                  .build());
      mock.enqueue(200, "application/json", "{\"sources\":[]}");
      client.sources().list();
      HttpMock.Recorded r = mock.requests.get(0);
      assertEquals("/v3/sources", r.path());
      assertEquals("finlight-client-java/" + FinlightClient.VERSION, r.header("user-agent"));
      assertEquals(FinlightClient.VERSION, r.header("x-client-version"));
      assertEquals(Kit.API_KEY, r.header("x-api-key"));
      client.close();
    }
  }

  @Test
  void defaults() {
    FinlightConfig c = FinlightConfig.builder("k").build();
    assertEquals("https://api.finlight.me", c.baseUrl());
    assertEquals("wss://wss.finlight.me", c.wssUrl());
    assertEquals(Duration.ofSeconds(30), c.timeout());
    assertEquals(3, c.maxRetries());
    assertEquals(RetryOnBurst.ONCE, c.retryOnBurst());
    assertEquals(Duration.ofSeconds(10), c.burstWait());
    assertEquals(10, c.maxPages());
    assertEquals(Duration.ofSeconds(25), c.pingInterval());
    assertEquals(Duration.ofSeconds(60), c.watchdogTimeout());
    assertEquals(Duration.ofMinutes(115), c.rotationInterval());
    assertEquals(Duration.ofSeconds(1), c.reconnectBaseDelay());
    assertEquals(Duration.ofSeconds(60), c.longBackoff());
    assertTrue(c.quotaReconnect());
    assertEquals(Duration.ofHours(1), c.quotaReconnectInterval());
    assertEquals(5, c.searchFailedThreshold());
    assertEquals(1000, c.dedupeCacheSize());
    assertEquals(32L * 1024 * 1024, c.maxMessageBytes());
    assertEquals(32L * 1024 * 1024, c.maxResponseBytes());
    assertEquals(Duration.ofSeconds(30), c.connectTimeout());
    assertThrows(
        FinlightValidationException.class, () -> FinlightConfig.builder("k").maxMessageBytes(1024));
  }

  @Test
  void anInjectedClientIsNeverClosed() throws Exception {
    java.net.http.HttpClient mine = java.net.http.HttpClient.newHttpClient();
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client =
          new FinlightClient(
              FinlightConfig.builder(Kit.API_KEY).baseUrl(mock.url()).httpClient(mine).build());
      client.close();
      mock.enqueue(200, "application/json", "{}");
      java.net.http.HttpResponse<String> r =
          mine.send(
              java.net.http.HttpRequest.newBuilder(java.net.URI.create(mock.url() + "/x")).build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      assertEquals(200, r.statusCode(), "the injected client still works");
    }
  }

  /** No key, secret, signature or body in any log line or error message, even at DEBUG. */
  @Test
  void secretsNeverReachLogsOrMessages() throws Exception {
    Logger jul = Logger.getLogger("finlight");
    List<String> lines = new ArrayList<>();
    Handler capture =
        new Handler() {
          @Override
          public void publish(LogRecord r) {
            lines.add(String.valueOf(r.getMessage()));
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Level saved = jul.getLevel();
    jul.setLevel(Level.ALL);
    jul.addHandler(capture);
    List<String> messages = new ArrayList<>();
    String key = "sk_secret_test_key_0123456789";
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient client =
          new FinlightClient(FinlightConfig.builder(key).baseUrl(mock.url()).clock(clock).build());
      messages.add(client.toString());
      messages.add(client.config().toString());
      mock.enqueue(
          401,
          "application/problem+json",
          "{\"code\":\"UNAUTHORIZED\",\"status\":401,\"detail\":\"Invalid API key\"}");
      messages.add(
          assertThrows(FinlightApiException.class, () -> client.articles().search()).getMessage());
      for (int i = 0; i < 4; i++) {
        mock.enqueue(502, "text/html", "<html>" + "body ".repeat(50) + "</html>");
      }
      messages.add(
          assertThrows(FinlightApiException.class, () -> client.sources().list()).getMessage());
      byte[] body = "{\"payloadVersion\":3}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      String sig = Support.sign(Support.SECRET, "2026-11-23T21:04:05.123Z", body);
      messages.add(
          assertThrows(
                  WebhookVerificationException.class,
                  () ->
                      Webhooks.verifySignature(
                          body, sig + "0", "2026-11-23T21:04:05.123Z", Support.SECRET))
              .getMessage());
      client.close();
    } finally {
      jul.removeHandler(capture);
      jul.setLevel(saved);
    }
    assertFalse(lines.isEmpty(), "DEBUG logging ran");
    for (String s : concat(lines, messages)) {
      assertFalse(s.contains(key), "key in: " + s);
      assertFalse(s.contains(Support.SECRET), "secret in: " + s);
      assertFalse(s.contains("body body"), "body in: " + s);
    }
  }

  @Test
  void creatingAClientInstallsNothingGlobal() throws Exception {
    Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
    Set<String> before = threads();
    FinlightClient client = new FinlightClient("k");
    assertEquals(handler, Thread.getDefaultUncaughtExceptionHandler());
    Set<String> added = threads();
    added.removeAll(before);
    assertTrue(
        added.stream().noneMatch(n -> n.startsWith("finlight")),
        "no SDK thread before a stream: " + added);
    assertNotNull(client.articles());
    client.close();
  }

  private static Set<String> threads() {
    return Thread.getAllStackTraces().keySet().stream()
        .map(Thread::getName)
        .collect(Collectors.toSet());
  }

  private static List<String> concat(List<String> a, List<String> b) {
    List<String> out = new ArrayList<>(a);
    out.addAll(b);
    return out;
  }
}
