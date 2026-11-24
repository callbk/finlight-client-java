package me.finlight.client;

import org.jspecify.annotations.Nullable;

/** The network failed. The transport's exception is the cause. */
public class FinlightNetworkException extends FinlightException {
  private static final long serialVersionUID = 1L;

  FinlightNetworkException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }

  @Override
  public FinlightErrorKind kind() {
    return FinlightErrorKind.NETWORK;
  }
}
