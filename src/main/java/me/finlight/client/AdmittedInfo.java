package me.finlight.client;

import org.jspecify.annotations.Nullable;

/**
 * A {@code connection.admitted} frame. It arrives before the server checks the subscribe; it does
 * not mean the subscribe was accepted.
 */
public final class AdmittedInfo {
  private final @Nullable String leaseId;
  private final @Nullable Long serverNow;
  private final @Nullable String clientNonce;

  AdmittedInfo(@Nullable String leaseId, @Nullable Long serverNow, @Nullable String clientNonce) {
    this.leaseId = leaseId;
    this.serverNow = serverNow;
    this.clientNonce = clientNonce;
  }

  /** The lease of this connection. */
  public @Nullable String leaseId() {
    return leaseId;
  }

  /** Server time in epoch milliseconds. */
  public @Nullable Long serverNow() {
    return serverNow;
  }

  /** The nonce the SDK sent with the subscribe, echoed. */
  public @Nullable String clientNonce() {
    return clientNonce;
  }

  @Override
  public String toString() {
    return "AdmittedInfo{leaseId=" + leaseId + ", clientNonce=" + clientNonce + "}";
  }
}
