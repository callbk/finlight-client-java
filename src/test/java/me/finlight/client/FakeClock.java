package me.finlight.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/** A clock that only moves when told to. Sleeps return at once and are recorded. */
final class FakeClock implements FinlightConfig.Clock {
  private static final class Task implements Comparable<Task> {
    final long due;
    final long seq;
    final Runnable run;
    volatile boolean cancelled;

    Task(long due, long seq, Runnable run) {
      this.due = due;
      this.seq = seq;
      this.run = run;
    }

    @Override
    public int compareTo(Task o) {
      return due != o.due ? Long.compare(due, o.due) : Long.compare(seq, o.seq);
    }
  }

  private final PriorityQueue<Task> tasks = new PriorityQueue<>();
  private long now = 1_795_467_845_123L;
  private long seq;
  final List<Duration> sleeps = new ArrayList<>();

  @Override
  public synchronized long millis() {
    return now;
  }

  @Override
  public void sleep(Duration duration) throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException();
    }
    synchronized (this) {
      sleeps.add(duration);
    }
    advance(duration.toMillis(), () -> {});
  }

  @Override
  public synchronized Runnable schedule(Duration delay, Runnable task) {
    Task t = new Task(now + delay.toMillis(), seq++, task);
    tasks.add(t);
    return () -> {
      synchronized (FakeClock.this) {
        t.cancelled = true;
        tasks.remove(t);
      }
    };
  }

  /** Timers not yet run and not cancelled. */
  synchronized int pending() {
    return (int) tasks.stream().filter(t -> !t.cancelled).count();
  }

  /**
   * Moves time forward by {@code ms}, running due timers in order. {@code settle} runs after each
   * timer so the code under test can react (and schedule more) before time moves on.
   */
  void advance(long ms, Runnable settle) {
    long target;
    synchronized (this) {
      target = now + ms;
    }
    while (true) {
      Task next;
      synchronized (this) {
        next = tasks.peek();
        if (next == null || next.due > target) {
          now = target;
          return;
        }
        tasks.poll();
        now = Math.max(now, next.due);
      }
      if (!next.cancelled) {
        next.run.run();
        settle.run();
      }
    }
  }
}
