package me.finlight.client;

import org.jspecify.annotations.Nullable;

/**
 * An element of a stream: {@link Created}, {@link Updated} or {@link Other} (an event this SDK
 * version does not know). {@code article.created} and {@code article.updated} for the same article
 * are two elements; exact repeats are dropped.
 *
 * <pre>{@code
 * if (event instanceof StreamEvent.Created created) {
 *   System.out.println(created.data().title());
 * }
 * }</pre>
 */
public abstract sealed class StreamEvent
    permits StreamEvent.Created, StreamEvent.Updated, StreamEvent.Other {
  private final StreamEventName event;
  private final String rawJson;

  private StreamEvent(StreamEventName event, String rawJson) {
    this.event = event;
    this.rawJson = rawJson;
  }

  /** {@code created}, {@code updated} or {@code other}. */
  public abstract StreamEventKind kind();

  /** The wire name of the frame, for example {@code article.created}. Open. */
  public StreamEventName event() {
    return event;
  }

  /** The article, or null for {@link Other}. */
  public abstract @Nullable Article data();

  /** The whole frame as received. */
  public String rawJson() {
    return rawJson;
  }

  @Override
  public String toString() {
    Article a = data();
    return "StreamEvent{" + kind() + ", " + event + (a == null ? "" : ", " + a.id()) + "}";
  }

  /** {@code article.created}: a new article (also the initial push after each subscribe). */
  public static final class Created extends StreamEvent {
    private final Article data;

    Created(StreamEventName event, Article data, String rawJson) {
      super(event, rawJson);
      this.data = data;
    }

    @Override
    public StreamEventKind kind() {
      return StreamEventKind.CREATED;
    }

    @Override
    public Article data() {
      return data;
    }
  }

  /** {@code article.updated}: needs {@code includeUpdates}. */
  public static final class Updated extends StreamEvent {
    private final Article data;

    Updated(StreamEventName event, Article data, String rawJson) {
      super(event, rawJson);
      this.data = data;
    }

    @Override
    public StreamEventKind kind() {
      return StreamEventKind.UPDATED;
    }

    @Override
    public Article data() {
      return data;
    }
  }

  /** An event this SDK version does not know. Not an error; the stream goes on. */
  public static final class Other extends StreamEvent {
    Other(StreamEventName event, String rawJson) {
      super(event, rawJson);
    }

    @Override
    public StreamEventKind kind() {
      return StreamEventKind.OTHER;
    }

    @Override
    public @Nullable Article data() {
      return null;
    }
  }
}
