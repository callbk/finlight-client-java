package me.finlight.client;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * A 429. {@link #limitType()} says which limit: {@code monthly} (never retried, wait for the next
 * period), {@code burst} (retried once after {@link #retryAfter()} by default), {@code unknown} or
 * a newer value sent by the server.
 */
public final class RateLimitedException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  private final RateLimitType limitType;
  private final @Nullable Duration retryAfter;

  RateLimitedException(ApiError e) {
    super(e);
    this.limitType = e.limitType == null ? RateLimitType.UNKNOWN : e.limitType;
    this.retryAfter = e.retryAfter;
  }

  /** Which limit was hit. Open: unknown values are kept. */
  public RateLimitType limitType() {
    return limitType;
  }

  /** The {@code Retry-After} header, or null when the server sent none. */
  public @Nullable Duration retryAfter() {
    return retryAfter;
  }
}
