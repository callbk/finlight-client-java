package me.finlight.client;

import org.jspecify.annotations.Nullable;

/** A request was rejected locally, before any request or dial. */
public final class FinlightValidationException extends FinlightException {
  private static final long serialVersionUID = 1L;

  private final @Nullable String param;

  FinlightValidationException(@Nullable String param, String message) {
    super(message, null);
    this.param = param;
  }

  /** The rejected parameter, or null. */
  public @Nullable String param() {
    return param;
  }

  @Override
  public FinlightErrorKind kind() {
    return FinlightErrorKind.VALIDATION;
  }
}
