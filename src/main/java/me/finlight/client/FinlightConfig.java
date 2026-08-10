package me.finlight.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the finlight client. Only the API key is required; all other fields fall back to
 * the documented defaults.
 *
 * <pre>{@code
 * FinlightConfig config = FinlightConfig.builder("your-api-key")
 *     .timeout(Duration.ofSeconds(10))
 *     .build();
 * }</pre>
 */
public final class FinlightConfig {

  /** Default REST endpoint. */
  public static final String DEFAULT_BASE_URL = "https://api.finlight.me";

  /** Default WebSocket endpoint. */
  public static final String DEFAULT_WSS_URL = "wss://wss.finlight.me";

  /** Default per-request timeout. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  /** Default total request attempts for retryable failures. */
  public static final int DEFAULT_RETRY_COUNT = 3;

  private final String apiKey;
  private final String baseUrl;
  private final String wssUrl;
  private final Duration timeout;
  private final int retryCount;
  private final @Nullable HttpClient httpClient;

  private FinlightConfig(Builder builder) {
    this.apiKey = builder.apiKey;
    this.baseUrl = builder.baseUrl;
    this.wssUrl = builder.wssUrl;
    this.timeout = builder.timeout;
    this.retryCount = builder.retryCount;
    this.httpClient = builder.httpClient;
  }

  /** Returns a configuration with defaults for the given API key. */
  public static FinlightConfig of(String apiKey) {
    return builder(apiKey).build();
  }

  /** Returns a new builder for the given API key. */
  public static Builder builder(String apiKey) {
    return new Builder(apiKey);
  }

  /** The finlight API key. */
  public String apiKey() {
    return apiKey;
  }

  /** Base URL of the REST API. */
  public String baseUrl() {
    return baseUrl;
  }

  /** URL of the WebSocket endpoint. */
  public String wssUrl() {
    return wssUrl;
  }

  /** Per-request timeout. */
  public Duration timeout() {
    return timeout;
  }

  /** Total request attempts for retryable failures. */
  public int retryCount() {
    return retryCount;
  }

  /** Custom HTTP client, or null to use a default one. */
  public @Nullable HttpClient httpClient() {
    return httpClient;
  }

  /** Builder for {@link FinlightConfig}. */
  public static final class Builder {
    private final String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private String wssUrl = DEFAULT_WSS_URL;
    private Duration timeout = DEFAULT_TIMEOUT;
    private int retryCount = DEFAULT_RETRY_COUNT;
    private @Nullable HttpClient httpClient;

    private Builder(String apiKey) {
      if (apiKey == null || apiKey.isBlank()) {
        throw new IllegalArgumentException("apiKey is required");
      }
      this.apiKey = apiKey;
    }

    /** Base URL of the REST API. */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
      return this;
    }

    /** URL of the WebSocket endpoint. */
    public Builder wssUrl(String wssUrl) {
      this.wssUrl = stripTrailingSlash(Objects.requireNonNull(wssUrl, "wssUrl"));
      return this;
    }

    /** Per-request timeout (also used as the WebSocket connect timeout). */
    public Builder timeout(Duration timeout) {
      this.timeout = Objects.requireNonNull(timeout, "timeout");
      return this;
    }

    /** Total request attempts for retryable failures (429 and transient 5xx). */
    public Builder retryCount(int retryCount) {
      if (retryCount < 1) {
        throw new IllegalArgumentException("retryCount must be >= 1");
      }
      this.retryCount = retryCount;
      return this;
    }

    /**
     * Custom {@link HttpClient} used for REST and WebSocket connections, e.g. to configure a proxy.
     * By default a client with the configured timeout is created.
     */
    public Builder httpClient(HttpClient httpClient) {
      this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
      return this;
    }

    /** Builds the immutable configuration. */
    public FinlightConfig build() {
      return new FinlightConfig(this);
    }

    private static String stripTrailingSlash(String url) {
      return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
  }
}
