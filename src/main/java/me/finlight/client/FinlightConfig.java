package me.finlight.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of a {@link FinlightClient}. Build it with {@link #builder(String)}; every setting
 * except the API key has a default. Times are {@link Duration}s.
 *
 * <pre>{@code
 * FinlightConfig config = FinlightConfig.builder(System.getenv("FINLIGHT_API_KEY"))
 *     .timeout(Duration.ofSeconds(10))
 *     .build();
 * }</pre>
 */
public final class FinlightConfig {
  /** Default REST base URL. */
  public static final String DEFAULT_BASE_URL = "https://api.finlight.me";

  /** Default WebSocket URL. Raw streams use {@code <wssUrl>/raw}. */
  public static final String DEFAULT_WSS_URL = "wss://wss.finlight.me";

  /**
   * Time source and timer for retries, backoff, pings, the watchdog and rotation. The SDK uses the
   * system clock unless you set one; tests replace it to control time without waiting.
   */
  public interface Clock {
    /** Current time in epoch milliseconds. */
    long millis();

    /** Blocks for {@code duration}; an interrupt ends the wait at once. */
    void sleep(Duration duration) throws InterruptedException;

    /**
     * Runs {@code task} once after {@code delay}, on any thread.
     *
     * @return an action that cancels the task if it has not run yet
     */
    Runnable schedule(Duration delay, Runnable task);
  }

  private final String apiKey;
  private final String baseUrl;
  private final String wssUrl;
  private final Duration timeout;
  private final @Nullable Duration totalTimeout;
  private final int maxRetries;
  private final Duration retryBaseDelay;
  private final Duration retryMaxDelay;
  private final RetryOnBurst retryOnBurst;
  private final Duration burstWait;
  private final int maxPages;
  private final long maxResponseBytes;
  private final int companyCacheSize;
  private final Duration pingInterval;
  private final Duration watchdogTimeout;
  private final Duration rotationInterval;
  private final Duration reconnectBaseDelay;
  private final Duration reconnectMaxDelay;
  private final Duration longBackoff;
  private final boolean quotaReconnect;
  private final Duration quotaReconnectInterval;
  private final int searchFailedThreshold;
  private final int dedupeCacheSize;
  private final long maxMessageBytes;
  private final Duration connectTimeout;
  private final @Nullable Clock clock;
  private final @Nullable HttpClient httpClient;

  private FinlightConfig(Builder b) {
    this.apiKey = b.apiKey;
    this.baseUrl = trimSlash(b.baseUrl);
    this.wssUrl = trimSlash(b.wssUrl);
    this.timeout = b.timeout;
    this.totalTimeout = b.totalTimeout;
    this.maxRetries = b.maxRetries;
    this.retryBaseDelay = b.retryBaseDelay;
    this.retryMaxDelay = b.retryMaxDelay;
    this.retryOnBurst = b.retryOnBurst;
    this.burstWait = b.burstWait;
    this.maxPages = b.maxPages;
    this.maxResponseBytes = b.maxResponseBytes;
    this.companyCacheSize = b.companyCacheSize;
    this.pingInterval = b.pingInterval;
    this.watchdogTimeout = b.watchdogTimeout;
    this.rotationInterval = b.rotationInterval;
    this.reconnectBaseDelay = b.reconnectBaseDelay;
    this.reconnectMaxDelay = b.reconnectMaxDelay;
    this.longBackoff = b.longBackoff;
    this.quotaReconnect = b.quotaReconnect;
    this.quotaReconnectInterval = b.quotaReconnectInterval;
    this.searchFailedThreshold = b.searchFailedThreshold;
    this.dedupeCacheSize = b.dedupeCacheSize;
    this.maxMessageBytes = b.maxMessageBytes;
    this.connectTimeout = b.connectTimeout;
    this.clock = b.clock;
    this.httpClient = b.httpClient;
  }

  private static String trimSlash(String url) {
    String u = url.trim();
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u;
  }

  /**
   * Starts a configuration.
   *
   * @param apiKey your API key; required and not empty
   * @throws FinlightValidationException when the key is null or blank
   */
  public static Builder builder(String apiKey) {
    return new Builder(apiKey);
  }

  /** A builder with the settings of this configuration. */
  public Builder toBuilder() {
    Builder b = new Builder(apiKey);
    b.baseUrl = baseUrl;
    b.wssUrl = wssUrl;
    b.timeout = timeout;
    b.totalTimeout = totalTimeout;
    b.maxRetries = maxRetries;
    b.retryBaseDelay = retryBaseDelay;
    b.retryMaxDelay = retryMaxDelay;
    b.retryOnBurst = retryOnBurst;
    b.burstWait = burstWait;
    b.maxPages = maxPages;
    b.maxResponseBytes = maxResponseBytes;
    b.companyCacheSize = companyCacheSize;
    b.pingInterval = pingInterval;
    b.watchdogTimeout = watchdogTimeout;
    b.rotationInterval = rotationInterval;
    b.reconnectBaseDelay = reconnectBaseDelay;
    b.reconnectMaxDelay = reconnectMaxDelay;
    b.longBackoff = longBackoff;
    b.quotaReconnect = quotaReconnect;
    b.quotaReconnectInterval = quotaReconnectInterval;
    b.searchFailedThreshold = searchFailedThreshold;
    b.dedupeCacheSize = dedupeCacheSize;
    b.maxMessageBytes = maxMessageBytes;
    b.connectTimeout = connectTimeout;
    b.clock = clock;
    b.httpClient = httpClient;
    return b;
  }

  /** The API key. Never logged and not part of {@link #toString()}. */
  public String apiKey() {
    return apiKey;
  }

  /** REST base URL, without a trailing slash. */
  public String baseUrl() {
    return baseUrl;
  }

  /** WebSocket URL, without a trailing slash. */
  public String wssUrl() {
    return wssUrl;
  }

  /** Timeout of one REST attempt. */
  public Duration timeout() {
    return timeout;
  }

  /** Optional cap per call, retries and waits included; null for none. */
  public @Nullable Duration totalTimeout() {
    return totalTimeout;
  }

  /** Retries after the first attempt for 5xx, network errors and timeouts. */
  public int maxRetries() {
    return maxRetries;
  }

  /** First backoff delay. */
  public Duration retryBaseDelay() {
    return retryBaseDelay;
  }

  /** Largest backoff delay. */
  public Duration retryMaxDelay() {
    return retryMaxDelay;
  }

  /** What a burst 429 does: {@link RetryOnBurst#ONCE} retries once after {@code Retry-After}. */
  public RetryOnBurst retryOnBurst() {
    return retryOnBurst;
  }

  /** Wait before the burst retry when the server sent no {@code Retry-After}. */
  public Duration burstWait() {
    return burstWait;
  }

  /** Default page limit of {@code searchAll}. */
  public int maxPages() {
    return maxPages;
  }

  /** Largest REST response body the SDK reads. */
  public long maxResponseBytes() {
    return maxResponseBytes;
  }

  /** Company records the SDK keeps for ETag revalidation; 0 turns the cache off. */
  public int companyCacheSize() {
    return companyCacheSize;
  }

  /** Interval of the application ping on streams. */
  public Duration pingInterval() {
    return pingInterval;
  }

  /** A stream connection without pong for this long is replaced. */
  public Duration watchdogTimeout() {
    return watchdogTimeout;
  }

  /** A stream renews its connection after this long (the gateway ends it at 2 h). */
  public Duration rotationInterval() {
    return rotationInterval;
  }

  /** First reconnect delay of a stream. */
  public Duration reconnectBaseDelay() {
    return reconnectBaseDelay;
  }

  /** Largest reconnect delay of a stream. */
  public Duration reconnectMaxDelay() {
    return reconnectMaxDelay;
  }

  /** Wait after a dial refused for the connection limit or a 429. */
  public Duration longBackoff() {
    return longBackoff;
  }

  /** Whether a stream waits for the WebSocket quota instead of ending. */
  public boolean quotaReconnect() {
    return quotaReconnect;
  }

  /** Interval of the quota retries. */
  public Duration quotaReconnectInterval() {
    return quotaReconnectInterval;
  }

  /** Consecutive {@code SEARCH_FAILED} frames before one goes to {@code onError}. */
  public int searchFailedThreshold() {
    return searchFailedThreshold;
  }

  /** Size of the duplicate filter of a stream. */
  public int dedupeCacheSize() {
    return dedupeCacheSize;
  }

  /** Largest stream message the SDK reads. */
  public long maxMessageBytes() {
    return maxMessageBytes;
  }

  /** Timeout of the TCP connect and the WebSocket handshake. */
  public Duration connectTimeout() {
    return connectTimeout;
  }

  /** The clock, or null for the system clock. */
  public @Nullable Clock clock() {
    return clock;
  }

  /** Your own HTTP client, or null when the SDK creates one. */
  public @Nullable HttpClient httpClient() {
    return httpClient;
  }

  @Override
  public String toString() {
    return "FinlightConfig{baseUrl="
        + baseUrl
        + ", wssUrl="
        + wssUrl
        + ", apiKey=<redacted>, timeout="
        + timeout
        + ", maxRetries="
        + maxRetries
        + ", retryOnBurst="
        + retryOnBurst
        + ", maxPages="
        + maxPages
        + ", quotaReconnect="
        + quotaReconnect
        + "}";
  }

  /** Builder of {@link FinlightConfig}. */
  public static final class Builder {
    private final String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private String wssUrl = DEFAULT_WSS_URL;
    private Duration timeout = Duration.ofSeconds(30);
    private @Nullable Duration totalTimeout;
    private int maxRetries = 3;
    private Duration retryBaseDelay = Duration.ofMillis(500);
    private Duration retryMaxDelay = Duration.ofSeconds(8);
    private RetryOnBurst retryOnBurst = RetryOnBurst.ONCE;
    private Duration burstWait = Duration.ofSeconds(10);
    private int maxPages = 10;
    private long maxResponseBytes = 32L * 1024 * 1024;
    private int companyCacheSize = 256;
    private Duration pingInterval = Duration.ofSeconds(25);
    private Duration watchdogTimeout = Duration.ofSeconds(60);
    private Duration rotationInterval = Duration.ofMinutes(115);
    private Duration reconnectBaseDelay = Duration.ofSeconds(1);
    private Duration reconnectMaxDelay = Duration.ofSeconds(30);
    private Duration longBackoff = Duration.ofSeconds(60);
    private boolean quotaReconnect = true;
    private Duration quotaReconnectInterval = Duration.ofHours(1);
    private int searchFailedThreshold = 5;
    private int dedupeCacheSize = 1000;
    private long maxMessageBytes = 32L * 1024 * 1024;
    private Duration connectTimeout = Duration.ofSeconds(30);
    private @Nullable Clock clock;
    private @Nullable HttpClient httpClient;

    private Builder(String apiKey) {
      if (apiKey == null || apiKey.isBlank()) {
        throw new FinlightValidationException("apiKey", "apiKey is required and must not be empty");
      }
      this.apiKey = apiKey;
    }

    /**
     * REST base URL. Default {@value FinlightConfig#DEFAULT_BASE_URL}. A trailing slash is fine.
     */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = url("baseUrl", baseUrl);
      return this;
    }

    /**
     * WebSocket URL. Default {@value FinlightConfig#DEFAULT_WSS_URL}; raw streams add {@code /raw}.
     */
    public Builder wssUrl(String wssUrl) {
      this.wssUrl = url("wssUrl", wssUrl);
      return this;
    }

    /** Timeout of one REST attempt. Default 30 s. */
    public Builder timeout(Duration timeout) {
      this.timeout = positive("timeout", timeout);
      return this;
    }

    /** Cap per call, retries and waits included. Default none. */
    public Builder totalTimeout(@Nullable Duration totalTimeout) {
      this.totalTimeout = totalTimeout == null ? null : positive("totalTimeout", totalTimeout);
      return this;
    }

    /** Retries for 5xx, network errors and timeouts. Default 3. */
    public Builder maxRetries(int maxRetries) {
      if (maxRetries < 0) {
        throw new FinlightValidationException("maxRetries", "maxRetries must be 0 or more");
      }
      this.maxRetries = maxRetries;
      return this;
    }

    /** First backoff delay. Default 0.5 s. */
    public Builder retryBaseDelay(Duration retryBaseDelay) {
      this.retryBaseDelay = notNegative("retryBaseDelay", retryBaseDelay);
      return this;
    }

    /** Largest backoff delay. Default 8 s. */
    public Builder retryMaxDelay(Duration retryMaxDelay) {
      this.retryMaxDelay = notNegative("retryMaxDelay", retryMaxDelay);
      return this;
    }

    /**
     * Burst 429: {@link RetryOnBurst#ONCE} (default) waits {@code Retry-After} ({@link #burstWait}
     * without the header) and tries once more; {@link RetryOnBurst#OFF} throws the error at once.
     */
    public Builder retryOnBurst(RetryOnBurst retryOnBurst) {
      this.retryOnBurst = Objects.requireNonNull(retryOnBurst, "retryOnBurst");
      return this;
    }

    /** Burst wait when the server sent no {@code Retry-After}. Default 10 s. */
    public Builder burstWait(Duration burstWait) {
      this.burstWait = notNegative("burstWait", burstWait);
      return this;
    }

    /** Default page limit of {@code searchAll}. Default 10. */
    public Builder maxPages(int maxPages) {
      if (maxPages < 1) {
        throw new FinlightValidationException("maxPages", "maxPages must be 1 or more");
      }
      this.maxPages = maxPages;
      return this;
    }

    /** Largest REST body the SDK reads. Default 32 MiB. */
    public Builder maxResponseBytes(long maxResponseBytes) {
      if (maxResponseBytes < 1) {
        throw new FinlightValidationException("maxResponseBytes", "maxResponseBytes must be > 0");
      }
      this.maxResponseBytes = maxResponseBytes;
      return this;
    }

    /** Company records kept for ETag revalidation. Default 256; 0 turns the cache off. */
    public Builder companyCacheSize(int companyCacheSize) {
      if (companyCacheSize < 0) {
        throw new FinlightValidationException("companyCacheSize", "companyCacheSize must be >= 0");
      }
      this.companyCacheSize = companyCacheSize;
      return this;
    }

    /** Stream ping interval. Default 25 s. */
    public Builder pingInterval(Duration pingInterval) {
      this.pingInterval = positive("pingInterval", pingInterval);
      return this;
    }

    /** Stream watchdog. Default 60 s. */
    public Builder watchdogTimeout(Duration watchdogTimeout) {
      this.watchdogTimeout = positive("watchdogTimeout", watchdogTimeout);
      return this;
    }

    /** Stream connection renewal. Default 115 min. */
    public Builder rotationInterval(Duration rotationInterval) {
      this.rotationInterval = positive("rotationInterval", rotationInterval);
      return this;
    }

    /** First stream reconnect delay. Default 1 s. */
    public Builder reconnectBaseDelay(Duration reconnectBaseDelay) {
      this.reconnectBaseDelay = positive("reconnectBaseDelay", reconnectBaseDelay);
      return this;
    }

    /** Largest stream reconnect delay. Default 30 s. */
    public Builder reconnectMaxDelay(Duration reconnectMaxDelay) {
      this.reconnectMaxDelay = positive("reconnectMaxDelay", reconnectMaxDelay);
      return this;
    }

    /** Wait after the connection limit or a dial 429. Default 60 s. */
    public Builder longBackoff(Duration longBackoff) {
      this.longBackoff = positive("longBackoff", longBackoff);
      return this;
    }

    /** Streams wait for the WebSocket quota (true, default) or end with the error (false). */
    public Builder quotaReconnect(boolean quotaReconnect) {
      this.quotaReconnect = quotaReconnect;
      return this;
    }

    /** Interval of the quota retries. Default 1 h. */
    public Builder quotaReconnectInterval(Duration quotaReconnectInterval) {
      this.quotaReconnectInterval = positive("quotaReconnectInterval", quotaReconnectInterval);
      return this;
    }

    /** Consecutive {@code SEARCH_FAILED} before {@code onError}. Default 5. */
    public Builder searchFailedThreshold(int searchFailedThreshold) {
      if (searchFailedThreshold < 1) {
        throw new FinlightValidationException(
            "searchFailedThreshold", "searchFailedThreshold must be 1 or more");
      }
      this.searchFailedThreshold = searchFailedThreshold;
      return this;
    }

    /** Duplicate filter size of a stream. Default 1000. */
    public Builder dedupeCacheSize(int dedupeCacheSize) {
      if (dedupeCacheSize < 1) {
        throw new FinlightValidationException("dedupeCacheSize", "dedupeCacheSize must be >= 1");
      }
      this.dedupeCacheSize = dedupeCacheSize;
      return this;
    }

    /** Largest stream message. Default 32 MiB, at least 16 MiB. */
    public Builder maxMessageBytes(long maxMessageBytes) {
      if (maxMessageBytes < 16L * 1024 * 1024) {
        throw new FinlightValidationException(
            "maxMessageBytes", "maxMessageBytes must be at least 16 MiB");
      }
      this.maxMessageBytes = maxMessageBytes;
      return this;
    }

    /** TCP connect and WebSocket handshake timeout. Default 30 s. */
    public Builder connectTimeout(Duration connectTimeout) {
      this.connectTimeout = positive("connectTimeout", connectTimeout);
      return this;
    }

    /** Replaces the clock (tests). Default: the system clock. */
    public Builder clock(@Nullable Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Your own {@link HttpClient} for REST and WebSocket (proxies, TLS, executors). Its connect
     * timeout and proxy apply; the SDK never closes it. Do not add your own retries: every attempt
     * costs quota.
     */
    public Builder httpClient(@Nullable HttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    /** Builds the configuration. */
    public FinlightConfig build() {
      return new FinlightConfig(this);
    }

    private static String url(String name, String value) {
      if (value == null || value.isBlank()) {
        throw new FinlightValidationException(name, name + " must not be empty");
      }
      try {
        URI uri = new URI(value.trim());
        if (uri.getScheme() == null || uri.getHost() == null) {
          throw new FinlightValidationException(name, name + " must be an absolute URL");
        }
      } catch (java.net.URISyntaxException e) {
        throw new FinlightValidationException(name, name + " is not a valid URL");
      }
      return value;
    }

    private static Duration positive(String name, Duration d) {
      Objects.requireNonNull(d, name);
      if (d.isNegative() || d.isZero()) {
        throw new FinlightValidationException(name, name + " must be positive");
      }
      return d;
    }

    private static Duration notNegative(String name, Duration d) {
      Objects.requireNonNull(d, name);
      if (d.isNegative()) {
        throw new FinlightValidationException(name, name + " must not be negative");
      }
      return d;
    }
  }
}
