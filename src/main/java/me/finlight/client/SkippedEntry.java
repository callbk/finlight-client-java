package me.finlight.client;

/**
 * An entry of a list that could not be read (R-MODELS-1). The rest of the list was delivered; this
 * one is kept here with its index and raw JSON so nothing is hidden.
 */
public final class SkippedEntry {
  private final int index;
  private final String raw;
  private final String reason;

  SkippedEntry(int index, String raw, String reason) {
    this.index = index;
    this.raw = raw;
    this.reason = reason;
  }

  /** Position in the list as sent. */
  public int index() {
    return index;
  }

  /** The entry as JSON text. */
  public String raw() {
    return raw;
  }

  /** Why it could not be read. */
  public String reason() {
    return reason;
  }

  @Override
  public String toString() {
    return "SkippedEntry{index=" + index + ", reason=" + reason + "}";
  }
}
