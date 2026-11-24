package me.finlight.client;

import org.jspecify.annotations.Nullable;

/**
 * A correctly signed webhook body that is not payload version 3: a delivery in the old format,
 * typically a retry queued before you switched the webhook. Not an error. Answer it with 2xx and
 * keep {@link #raw()} if you need the data; never answer with 410.
 */
public final class WrongPayloadVersion implements WebhookPayload {
  private final @Nullable String payloadVersion;
  private final String raw;
  private final @Nullable String link;

  WrongPayloadVersion(@Nullable String payloadVersion, String raw, @Nullable String link) {
    this.payloadVersion = payloadVersion;
    this.raw = raw;
    this.link = link;
  }

  /** The version the server would detect: always 2 (only the number 3 means v3). */
  public int detectedVersion() {
    return 2;
  }

  /**
   * The {@code payloadVersion} value as received, as JSON text (for example {@code "3"} with quotes
   * for a string), or null when the key was missing.
   */
  public @Nullable String payloadVersion() {
    return payloadVersion;
  }

  /** True when the body carried a {@code payloadVersion} key. */
  public boolean payloadVersionPresent() {
    return payloadVersion != null;
  }

  /** The body as JSON text. */
  public String raw() {
    return raw;
  }

  @Override
  public String rawJson() {
    return raw;
  }

  /** The article link, when the body has a readable one. */
  public @Nullable String link() {
    return link;
  }

  @Override
  public String toString() {
    return "WrongPayloadVersion{payloadVersion=" + payloadVersion + ", link=" + link + "}";
  }
}
