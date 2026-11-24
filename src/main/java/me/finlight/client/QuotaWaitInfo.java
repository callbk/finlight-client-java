package me.finlight.client;

import java.time.Duration;

/** The stream waits for the WebSocket quota of the next period (see {@code quotaReconnect}). */
public final class QuotaWaitInfo {
  private final FinlightException error;
  private final Duration nextAttemptIn;
  private final int attempt;

  QuotaWaitInfo(FinlightException error, Duration nextAttemptIn, int attempt) {
    this.error = error;
    this.nextAttemptIn = nextAttemptIn;
    this.attempt = attempt;
  }

  /** The {@link PreemptedException} or the refused dial ({@link ForbiddenException}, 403). */
  public FinlightException error() {
    return error;
  }

  /** Time until the next dial. */
  public Duration nextAttemptIn() {
    return nextAttemptIn;
  }

  /** Consecutive quota waits, starting at 1. */
  public int attempt() {
    return attempt;
  }

  @Override
  public String toString() {
    return "QuotaWaitInfo{attempt=" + attempt + ", nextAttemptIn=" + nextAttemptIn + "}";
  }
}
