package me.finlight.client;

/** What a {@link StreamEvent} carries. */
public enum StreamEventKind {
  /** {@code article.created}. */
  CREATED("created"),
  /** {@code article.updated}. */
  UPDATED("updated"),
  /** An event this SDK version does not know; skip it or read {@link StreamEvent#rawJson()}. */
  OTHER("other");

  private final String value;

  StreamEventKind(String value) {
    this.value = value;
  }

  /** The kind as text: {@code created}, {@code updated} or {@code other}. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
