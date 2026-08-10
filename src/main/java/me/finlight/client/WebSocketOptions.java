package me.finlight.client;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Tuning options of the WebSocket streaming clients. The defaults match the sibling clients
 * (TypeScript, Python, Go) and are safe for production use.
 */
public final class WebSocketOptions {

  /** Application-level ping cadence, default 25s. */
  public static final Duration DEFAULT_PING_INTERVAL = Duration.ofSeconds(25);

  /** Force a reconnect when no pong arrives within this window, default 60s. */
  public static final Duration DEFAULT_PONG_TIMEOUT = Duration.ofSeconds(60);

  /** First reconnect backoff, default 500ms. */
  public static final Duration DEFAULT_BASE_RECONNECT_DELAY = Duration.ofMillis(500);

  /** Backoff cap, default 10s. */
  public static final Duration DEFAULT_MAX_RECONNECT_DELAY = Duration.ofSeconds(10);

  /** Proactive connection rotation, default 115min (under the 2h server cap). */
  public static final Duration DEFAULT_CONNECTION_LIFETIME = Duration.ofMinutes(115);

  private final Duration pingInterval;
  private final Duration pongTimeout;
  private final Duration baseReconnectDelay;
  private final Duration maxReconnectDelay;
  private final Duration connectionLifetime;
  private final boolean takeover;
  private final @Nullable BiConsumer<Integer, String> onClose;

  private WebSocketOptions(Builder builder) {
    this.pingInterval = builder.pingInterval;
    this.pongTimeout = builder.pongTimeout;
    this.baseReconnectDelay = builder.baseReconnectDelay;
    this.maxReconnectDelay = builder.maxReconnectDelay;
    this.connectionLifetime = builder.connectionLifetime;
    this.takeover = builder.takeover;
    this.onClose = builder.onClose;
  }

  /** Returns the default options. */
  public static WebSocketOptions defaults() {
    return builder().build();
  }

  /** Returns a new builder with default values. */
  public static Builder builder() {
    return new Builder();
  }

  /** Application-level ping cadence. */
  public Duration pingInterval() {
    return pingInterval;
  }

  /** Window after which a missing pong forces a reconnect. */
  public Duration pongTimeout() {
    return pongTimeout;
  }

  /** First reconnect backoff. */
  public Duration baseReconnectDelay() {
    return baseReconnectDelay;
  }

  /** Backoff cap. */
  public Duration maxReconnectDelay() {
    return maxReconnectDelay;
  }

  /** Proactive connection rotation interval. */
  public Duration connectionLifetime() {
    return connectionLifetime;
  }

  /** Whether to take over an existing connection for the same API key. */
  public boolean takeover() {
    return takeover;
  }

  /** Callback invoked with (code, reason) whenever a connection closes, or null. */
  public @Nullable BiConsumer<Integer, String> onClose() {
    return onClose;
  }

  /** Builder for {@link WebSocketOptions}. */
  public static final class Builder {
    private Duration pingInterval = DEFAULT_PING_INTERVAL;
    private Duration pongTimeout = DEFAULT_PONG_TIMEOUT;
    private Duration baseReconnectDelay = DEFAULT_BASE_RECONNECT_DELAY;
    private Duration maxReconnectDelay = DEFAULT_MAX_RECONNECT_DELAY;
    private Duration connectionLifetime = DEFAULT_CONNECTION_LIFETIME;
    private boolean takeover;
    private @Nullable BiConsumer<Integer, String> onClose;

    private Builder() {}

    /** Application-level ping cadence. */
    public Builder pingInterval(Duration pingInterval) {
      this.pingInterval = Objects.requireNonNull(pingInterval, "pingInterval");
      return this;
    }

    /** Window after which a missing pong forces a reconnect. */
    public Builder pongTimeout(Duration pongTimeout) {
      this.pongTimeout = Objects.requireNonNull(pongTimeout, "pongTimeout");
      return this;
    }

    /** First reconnect backoff. */
    public Builder baseReconnectDelay(Duration baseReconnectDelay) {
      this.baseReconnectDelay = Objects.requireNonNull(baseReconnectDelay, "baseReconnectDelay");
      return this;
    }

    /** Backoff cap. */
    public Builder maxReconnectDelay(Duration maxReconnectDelay) {
      this.maxReconnectDelay = Objects.requireNonNull(maxReconnectDelay, "maxReconnectDelay");
      return this;
    }

    /** Proactive connection rotation interval. */
    public Builder connectionLifetime(Duration connectionLifetime) {
      this.connectionLifetime = Objects.requireNonNull(connectionLifetime, "connectionLifetime");
      return this;
    }

    /** Whether to take over an existing connection for the same API key. */
    public Builder takeover(boolean takeover) {
      this.takeover = takeover;
      return this;
    }

    /** Callback invoked with (code, reason) whenever a connection closes. */
    public Builder onClose(BiConsumer<Integer, String> onClose) {
      this.onClose = Objects.requireNonNull(onClose, "onClose");
      return this;
    }

    /** Builds the immutable options. */
    public WebSocketOptions build() {
      return new WebSocketOptions(this);
    }
  }
}
