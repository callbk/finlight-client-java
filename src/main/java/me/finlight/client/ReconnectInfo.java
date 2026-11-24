package me.finlight.client;

import org.jspecify.annotations.Nullable;

/**
 * A stream opened a new connection (not sent for the first one). The SDK does not backfill: use
 * {@link #lastSeenAt()} to fill a gap through REST if you need to.
 */
public final class ReconnectInfo {
  private final @Nullable String lastSeenAt;
  private final ReconnectReason reason;
  private final int connection;

  ReconnectInfo(@Nullable String lastSeenAt, ReconnectReason reason, int connection) {
    this.lastSeenAt = lastSeenAt;
    this.reason = reason;
    this.connection = connection;
  }

  /**
   * The latest timestamp seen on this stream: {@code availableAt} (or {@code firstSeenAt} when that
   * is null) for enriched streams, {@code firstSeenAt} for raw streams; null when nothing came yet.
   */
  public @Nullable String lastSeenAt() {
    return lastSeenAt;
  }

  /** Why the stream reconnected. */
  public ReconnectReason reason() {
    return reason;
  }

  /** Number of connections of this stream so far, the new one included. */
  public int connection() {
    return connection;
  }

  @Override
  public String toString() {
    return "ReconnectInfo{reason="
        + reason
        + ", connection="
        + connection
        + ", lastSeenAt="
        + lastSeenAt
        + "}";
  }
}
