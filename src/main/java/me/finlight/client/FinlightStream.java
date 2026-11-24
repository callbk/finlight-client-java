package me.finlight.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Handle of a running stream. The stream pings every 25 s, replaces a connection without pong for
 * 60 s, renews the connection after 115 min, re-sends the same subscribe after every reconnect and
 * drops duplicates. {@link #close()} closes the socket with 1000 and stops every timer and thread.
 */
public final class FinlightStream implements AutoCloseable {
  private final StreamEngine engine;

  FinlightStream(StreamEngine engine) {
    this.engine = engine;
  }

  /** Stops the stream. Safe from any thread, also from inside a listener; idempotent. */
  @Override
  public void close() {
    engine.stop();
  }

  /**
   * Blocks until the stream has ended and {@link StreamListener#onClosed} has run.
   *
   * @return the terminal error, or null when the stream was closed
   * @throws InterruptedException when the waiting thread is interrupted
   */
  public @Nullable FinlightException awaitTermination() throws InterruptedException {
    engine.finished().await();
    return engine.terminal();
  }

  /**
   * Blocks until the stream has ended, at most {@code timeout}.
   *
   * @return true when the stream ended
   * @throws InterruptedException when the waiting thread is interrupted
   */
  public boolean awaitTermination(Duration timeout) throws InterruptedException {
    return engine.finished().await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** The error that ended the stream, or null. */
  public @Nullable FinlightException terminalError() {
    return engine.terminal();
  }

  /** See {@link ReconnectInfo#lastSeenAt()}. */
  public @Nullable String lastSeenAt() {
    return engine.lastSeenAt();
  }

  StreamEngine engine() {
    return engine;
  }

  /** True once the stream has ended. */
  public boolean closed() {
    return engine.finished().getCount() == 0;
  }
}
