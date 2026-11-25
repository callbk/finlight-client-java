package me.finlight.client;

/** Options of {@code client.stream} and {@code client.rawStream}. */
public final class StreamOptions {
  private static final StreamOptions DEFAULTS = new StreamOptions(false);

  private final boolean takeover;

  private StreamOptions(boolean takeover) {
    this.takeover = takeover;
  }

  /** The defaults: no takeover. */
  public static StreamOptions defaults() {
    return DEFAULTS;
  }

  /**
   * With takeover on, a dial at the connection limit replaces your stalest connection (it gets
   * {@code connection.preempted}) instead of being refused. Default off.
   */
  public static StreamOptions takeover(boolean takeover) {
    return new StreamOptions(takeover);
  }

  /** Whether the stream dials with {@code x-takeover: true}. */
  public boolean takeover() {
    return takeover;
  }
}
