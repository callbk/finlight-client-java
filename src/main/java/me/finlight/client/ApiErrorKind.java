package me.finlight.client;

/**
 * The kind of a {@link FinlightApiException}. {@link #OTHER} covers unknown codes and bodies
 * without one.
 */
public enum ApiErrorKind {
  /** {@code VALIDATION_FAILED}, or a 400 in the fallback form. */
  VALIDATION_FAILED("validation_failed"),
  /** {@code CURSOR_INVALID}. */
  CURSOR_INVALID("cursor_invalid"),
  /** {@code CURSOR_MISMATCH}. */
  CURSOR_MISMATCH("cursor_mismatch"),
  /** {@code UNAUTHORIZED}: missing or invalid API key. */
  UNAUTHORIZED("unauthorized"),
  /** {@code FORBIDDEN}: the plan does not include it. */
  FORBIDDEN("forbidden"),
  /** {@code NOT_FOUND}. */
  NOT_FOUND("not_found"),
  /** {@code RATE_LIMITED}: a 429. */
  RATE_LIMITED("rate_limited"),
  /** {@code INTERNAL_ERROR}. */
  INTERNAL_ERROR("internal_error"),
  /** An unknown code, or an answer without a code. */
  OTHER("other");

  private final String value;

  ApiErrorKind(String value) {
    this.value = value;
  }

  /** The kind as text, for example {@code "not_found"}. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
