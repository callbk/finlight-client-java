package me.finlight.client;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Options of {@link Webhooks}: the timestamp tolerance and the clock. */
public final class WebhookVerifyOptions {
  private static final WebhookVerifyOptions DEFAULTS =
      new WebhookVerifyOptions(Duration.ofSeconds(300), Clock.systemUTC());

  private final Duration tolerance;
  private final Clock now;

  private WebhookVerifyOptions(Duration tolerance, Clock now) {
    this.tolerance = tolerance;
    this.now = now;
  }

  /** Tolerance 300 s, system clock. */
  public static WebhookVerifyOptions defaults() {
    return DEFAULTS;
  }

  /** A copy with another tolerance (applies in both directions). */
  public WebhookVerifyOptions tolerance(Duration tolerance) {
    Objects.requireNonNull(tolerance, "tolerance");
    if (tolerance.isNegative()) {
      throw new FinlightValidationException("tolerance", "tolerance must not be negative");
    }
    return new WebhookVerifyOptions(tolerance, now);
  }

  /** A copy with another clock (tests). */
  public WebhookVerifyOptions now(Clock now) {
    return new WebhookVerifyOptions(tolerance, Objects.requireNonNull(now, "now"));
  }

  /** The tolerance. */
  public Duration tolerance() {
    return tolerance;
  }

  /** The clock. */
  public Clock now() {
    return now;
  }
}
