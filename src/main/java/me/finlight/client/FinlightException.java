package me.finlight.client;

/** Base class of all exceptions thrown by the finlight client. */
public class FinlightException extends RuntimeException {

  public FinlightException(String message) {
    super(message);
  }

  public FinlightException(String message, Throwable cause) {
    super(message, cause);
  }
}
