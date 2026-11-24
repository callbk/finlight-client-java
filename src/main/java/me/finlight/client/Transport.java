package me.finlight.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/**
 * REST transport with the retry policy of R-RETRY-1: 400, 401, 403, 404 and monthly or unknown 429
 * are never retried; a burst 429 waits {@code Retry-After} (or {@code burstWait}) and tries once
 * more; 5xx, network errors and attempt timeouts are retried up to {@code maxRetries} times with
 * jittered backoff. One {@code X-Request-Id} per logical call, kept on retries.
 */
final class Transport {
  static final String USER_AGENT = "finlight-client-java/" + BuildInfo.VERSION;

  private final FinlightConfig config;
  private final HttpClient http;

  Transport(FinlightConfig config, HttpClient http) {
    this.config = config;
    this.http = http;
  }

  /** A request: method, path, query pairs (already final values), optional JSON body. */
  record Request(
      String method,
      String path,
      List<Map.Entry<String, String>> query,
      byte @Nullable [] body,
      Map<String, String> headers) {}

  /** A response with a status of 2xx or 304. */
  record Response(int status, HttpResponseHeaders headers, String body, String requestId) {}

  /** Response headers, read case-insensitively. */
  record HttpResponseHeaders(java.net.http.HttpHeaders headers) {
    @Nullable String first(String name) {
      return headers.firstValue(name).orElse(null);
    }
  }

  Response send(Request req) {
    String requestId = "req_" + UUID.randomUUID();
    Duration total = config.totalTimeout();
    long deadline = total == null ? Long.MAX_VALUE : now() + total.toMillis();
    int retries = 0;
    boolean burstRetried = false;
    while (true) {
      Raw resp;
      try {
        resp = attempt(req, requestId, deadline);
      } catch (FinlightNetworkException e) {
        if (retries >= config.maxRetries()) {
          throw e;
        }
        Duration wait = backoff(retries++);
        Log.debug(
            () ->
                req.method()
                    + " "
                    + req.path()
                    + " failed ("
                    + e.getMessage()
                    + "), retry in "
                    + wait.toMillis()
                    + " ms");
        pause(wait, deadline, e);
        continue;
      }
      int status = resp.statusCode();
      if ((status >= 200 && status < 300) || status == 304) {
        Log.debug(() -> req.method() + " " + req.path() + " -> " + status + " (" + requestId + ")");
        return new Response(
            status, new HttpResponseHeaders(resp.headers()), resp.body(), requestId);
      }
      ApiError parsed =
          Problems.parse(
              status,
              resp.headers().firstValue("content-type").orElse(null),
              resp.body(),
              requestId,
              resp.headers().firstValue("retry-after").orElse(null));
      FinlightApiException error = parsed.build();
      Log.debug(() -> req.method() + " " + req.path() + " -> " + status + " " + error.apiKind());
      if (error instanceof RateLimitedException rl) {
        if (RateLimitType.BURST.equals(rl.limitType()) && config.retryOnBurst() && !burstRetried) {
          burstRetried = true;
          Duration wait = rl.retryAfter() != null ? rl.retryAfter() : config.burstWait();
          Log.warn(
              "burst rate limit hit; retrying once in "
                  + wait.toMillis()
                  + " ms ("
                  + requestId
                  + ")");
          pause(wait, deadline, error);
          continue;
        }
        throw error;
      }
      if (status >= 500 && retries < config.maxRetries()) {
        Duration wait = backoff(retries++);
        Log.debug(() -> "server error " + status + ", retry in " + wait.toMillis() + " ms");
        pause(wait, deadline, error);
        continue;
      }
      throw error;
    }
  }

  private Raw attempt(Request req, String requestId, long deadline) {
    StringBuilder url = new StringBuilder(config.baseUrl()).append(req.path());
    char sep = '?';
    for (Map.Entry<String, String> q : req.query()) {
      url.append(sep)
          .append(Query.encode(q.getKey()))
          .append('=')
          .append(Query.encode(q.getValue()));
      sep = '&';
    }
    Duration timeout = config.timeout();
    if (deadline != Long.MAX_VALUE) {
      long left = deadline - now();
      if (left <= 0) {
        throw new FinlightTimeoutException("the call ran out of its total timeout", null);
      }
      timeout = Duration.ofMillis(Math.min(timeout.toMillis(), left));
    }
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(url.toString()))
            .timeout(timeout)
            .header("X-API-KEY", config.apiKey())
            .header("User-Agent", USER_AGENT)
            .header("x-client-version", BuildInfo.VERSION)
            .header("Accept", "application/json")
            .header("X-Request-Id", requestId);
    req.headers().forEach(b::header);
    if (req.body() != null) {
      b.header("Content-Type", "application/json");
      b.method(req.method(), HttpRequest.BodyPublishers.ofByteArray(req.body()));
    } else {
      b.method(req.method(), HttpRequest.BodyPublishers.noBody());
    }
    try {
      HttpResponse<InputStream> resp =
          http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
      String body = read(resp.body());
      return new Raw(resp.statusCode(), resp.headers(), body);
    } catch (HttpTimeoutException e) {
      throw new FinlightTimeoutException(
          "the request timed out after " + timeout.toMillis() + " ms", e);
    } catch (IOException e) {
      throw new FinlightNetworkException("the request failed: " + e.getClass().getSimpleName(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      CancellationException c = new CancellationException("the call was interrupted");
      c.initCause(e);
      throw c;
    }
  }

  private String read(InputStream in) throws IOException {
    try (InputStream s = in) {
      long max = config.maxResponseBytes();
      byte[] data = s.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
      if (data.length > max) {
        throw new FinlightDecodeException(
            "the response is larger than maxResponseBytes (" + max + ")", null, null, null);
      }
      return new String(data, StandardCharsets.UTF_8);
    }
  }

  private Duration backoff(int retry) {
    long base = config.retryBaseDelay().toMillis();
    long max = config.retryMaxDelay().toMillis();
    long capped = Math.min(max, base * (1L << Math.min(retry, 20)));
    double jitter = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;
    return Duration.ofMillis(Math.round(capped * jitter));
  }

  private void pause(Duration wait, long deadline, FinlightException cause) {
    if (deadline != Long.MAX_VALUE && now() + wait.toMillis() > deadline) {
      throw cause;
    }
    sleep(config.clock(), wait);
  }

  private long now() {
    FinlightConfig.Clock c = config.clock();
    return c == null ? System.currentTimeMillis() : c.millis();
  }

  /** Sleeps on the clock; an interrupt ends the wait at once and surfaces as cancellation. */
  static void sleep(FinlightConfig.@Nullable Clock clock, Duration wait) {
    try {
      if (clock == null) {
        Thread.sleep(wait.toMillis());
      } else {
        clock.sleep(wait);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      CancellationException c = new CancellationException("the call was interrupted");
      c.initCause(e);
      throw c;
    }
  }

  /** One answer with its body read. */
  private record Raw(int statusCode, java.net.http.HttpHeaders headers, String body) {}
}
