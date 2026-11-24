package me.finlight.client;

import org.jspecify.annotations.Nullable;

/** A response could not be read: a required key is missing or a value has the wrong JSON type. */
public final class FinlightDecodeException extends FinlightException {
  private static final long serialVersionUID = 1L;

  private final @Nullable String path;
  private final @Nullable String raw;

  FinlightDecodeException(
      String message, @Nullable String path, @Nullable String raw, @Nullable Throwable cause) {
    super(message, cause);
    this.path = path;
    this.raw = raw;
  }

  /** JSON path of the value that did not fit, or null. */
  public @Nullable String path() {
    return path;
  }

  /** The JSON that did not fit, or null. */
  public @Nullable String raw() {
    return raw;
  }

  @Override
  public FinlightErrorKind kind() {
    return FinlightErrorKind.DECODE;
  }
}
