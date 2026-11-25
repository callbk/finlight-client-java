package me.finlight.client;

import org.jspecify.annotations.Nullable;

/**
 * The server ended a stream with {@code connection.preempted}. Reason {@code connection_limit}
 * (another connection took over) ends the stream. Reason {@code quota_exceeded} ends it only when
 * {@code quotaReconnect} is off; otherwise it goes to {@link StreamListener#onQuotaWait}.
 */
public final class PreemptedException extends FinlightException {
  private static final long serialVersionUID = 1L;

  private final PreemptReason reason;
  private final @Nullable String newLeaseId;
  private final @Nullable Long timestamp;

  PreemptedException(PreemptReason reason, @Nullable String newLeaseId, @Nullable Long timestamp) {
    super(
        "stream preempted: "
            + reason.value()
            + (newLeaseId == null ? "" : " (new lease " + newLeaseId + ")"),
        null);
    this.reason = reason;
    this.newLeaseId = newLeaseId;
    this.timestamp = timestamp;
  }

  /** Why the server preempted the connection. */
  public PreemptReason reason() {
    return reason;
  }

  /** The lease of the connection that took over, or null. */
  public @Nullable String newLeaseId() {
    return newLeaseId;
  }

  /** Server time of the preemption in epoch milliseconds, or null. */
  public @Nullable Long timestamp() {
    return timestamp;
  }

  @Override
  public FinlightErrorKind kind() {
    return FinlightErrorKind.PREEMPTED;
  }
}
