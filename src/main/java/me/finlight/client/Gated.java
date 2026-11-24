package me.finlight.client;

import java.util.NoSuchElementException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A key that depends on the plan or on {@code include} (R-NULL-1). It has three states:
 *
 * <ul>
 *   <li>{@link Absent}: the key was not sent (not requested, not entitled, or a raw article);
 *   <li>{@link Present} with a null value: requested and entitled, but there is no value;
 *   <li>{@link Present} with a value.
 * </ul>
 *
 * <p>{@code companies}, {@code events}, {@code events[].companyIds} and {@code Source.hasContent}
 * are never null when present: an empty list means "tagged, nothing found". Absent is never turned
 * into an empty list, false or zero.
 *
 * @param <T> the value type
 */
public sealed interface Gated<T> permits Gated.Absent, Gated.Present {

  /** The absent state. */
  @SuppressWarnings("unchecked")
  static <T> Gated<T> absent() {
    return (Gated<T>) Absent.INSTANCE;
  }

  /** A present key with the given value (null means "present, no value"). */
  static <T> Gated<T> of(@Nullable T value) {
    return new Present<>(value);
  }

  /** True when the key was in the JSON, even with the value null. */
  boolean isPresent();

  /** The value, or null when absent or null. Use {@link #isPresent()} to tell the two apart. */
  @Nullable T orNull();

  /**
   * The value of a present key.
   *
   * @throws NoSuchElementException when the key was absent
   */
  @Nullable T value();

  /** Applies {@code f} to a present non-null value. Absent stays absent, null stays null. */
  <R> Gated<R> map(Function<? super T, ? extends R> f);

  /** The key was not sent. */
  final class Absent<T> implements Gated<T> {
    static final Absent<Object> INSTANCE = new Absent<>();

    private Absent() {}

    @Override
    public boolean isPresent() {
      return false;
    }

    @Override
    public @Nullable T orNull() {
      return null;
    }

    @Override
    public T value() {
      throw new NoSuchElementException("the key was not sent (not requested or not entitled)");
    }

    @Override
    public <R> Gated<R> map(Function<? super T, ? extends R> f) {
      return absent();
    }

    @Override
    public String toString() {
      return "Gated.Absent";
    }
  }

  /**
   * The key was sent.
   *
   * @param value the value, null when the key was sent as null
   * @param <T> the value type
   */
  record Present<T>(@Nullable T value) implements Gated<T> {
    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public @Nullable T orNull() {
      return value;
    }

    @Override
    public <R> Gated<R> map(Function<? super T, ? extends R> f) {
      return value == null ? new Present<>(null) : new Present<>(f.apply(value));
    }
  }
}
