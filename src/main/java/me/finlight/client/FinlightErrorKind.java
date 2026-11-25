package me.finlight.client;

/** The kind of a {@link FinlightException}, readable without {@code instanceof}. */
public enum FinlightErrorKind {
  /** The API answered with an error ({@link FinlightApiException}). */
  API("api"),
  /** A request was rejected locally, before anything was sent. */
  VALIDATION("validation"),
  /** The network failed. */
  NETWORK("network"),
  /** An attempt timed out. */
  TIMEOUT("timeout"),
  /** A response could not be read. */
  DECODE("decode"),
  /** A webhook failed verification. */
  WEBHOOK_VERIFICATION("webhook_verification"),
  /** Another connection or the quota ended a stream. */
  PREEMPTED("preempted");

  private final String value;

  FinlightErrorKind(String value) {
    this.value = value;
  }

  /** The kind as text, for example {@code "api"}. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
