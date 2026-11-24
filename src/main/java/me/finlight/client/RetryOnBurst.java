package me.finlight.client;

/** What a REST call does on a burst 429 ({@link FinlightConfig.Builder#retryOnBurst}). */
public enum RetryOnBurst {
  /** Wait {@code Retry-After} ({@code burstWait} without the header) and try once more. Default. */
  ONCE,
  /** Throw the {@link RateLimitedException} at once. */
  OFF
}
