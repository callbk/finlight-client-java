package me.finlight.client;

import org.jspecify.annotations.Nullable;

/**
 * Base of every error the SDK throws. {@link #kind()} names the kind without {@code instanceof}.
 * Errors of other libraries are wrapped; the original is {@link #getCause()}.
 *
 * <p>Cancelling a call (interrupting its thread) is not a {@code FinlightException}: it surfaces as
 * {@link java.util.concurrent.CancellationException} with the interrupt flag restored.
 */
public abstract class FinlightException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  FinlightException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }

  /** The kind of this error. */
  public abstract FinlightErrorKind kind();
}
