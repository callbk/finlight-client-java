package me.finlight.client;

import org.jspecify.annotations.Nullable;

/** An attempt, a dial or the whole call ran out of time. */
public final class FinlightTimeoutException extends FinlightNetworkException {
  private static final long serialVersionUID = 1L;

  FinlightTimeoutException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }

  @Override
  public FinlightErrorKind kind() {
    return FinlightErrorKind.TIMEOUT;
  }
}
