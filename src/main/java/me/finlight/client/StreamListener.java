package me.finlight.client;

import org.jspecify.annotations.Nullable;

/**
 * Receives a stream. All methods run on one daemon thread of the stream, in order; only {@link
 * #onEvent} is required. An exception thrown here is logged and does not end the stream.
 */
@FunctionalInterface
public interface StreamListener {
  /** An article event, or an event this SDK version does not know ({@link StreamEvent.Other}). */
  void onEvent(StreamEvent event);

  /** Each {@code connection.admitted}. */
  default void onAdmitted(AdmittedInfo info) {}

  /** After each new connection except the first. The SDK does not backfill. */
  default void onReconnect(ReconnectInfo info) {}

  /** A problem that does not end the stream. */
  default void onError(FinlightException error) {}

  /** The stream waits for the WebSocket quota; logged as a warning, not sent to onError. */
  default void onQuotaWait(QuotaWaitInfo info) {}

  /**
   * The stream ended. {@code terminal} is the error that ended it, or null after {@link
   * FinlightStream#close()}.
   */
  default void onClosed(@Nullable FinlightException terminal) {}
}
