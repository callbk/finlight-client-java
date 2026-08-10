package me.finlight.client;

/**
 * Thrown by the WebSocket clients when the server permanently rejected the connection (close code
 * 1008). Reconnecting will not help; contact finlight support.
 */
public class FinlightBlockedException extends FinlightException {

  public FinlightBlockedException() {
    super("finlight: connection rejected by server (blocked)");
  }
}
