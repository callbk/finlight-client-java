package me.finlight.client;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * HTTP caching data of a company record from {@code companies().get(id)}. The SDK keeps no cache of
 * its own: keep the record yourself, reuse it within {@code max-age}, and pass {@link #etag()} back
 * as {@code ifNoneMatch} to revalidate it.
 */
public final class CacheInfo {
  private final @Nullable String etag;
  private final @Nullable String cacheControl;

  CacheInfo(@Nullable String etag, @Nullable String cacheControl) {
    this.etag = etag;
    this.cacheControl = cacheControl;
  }

  /** The {@code ETag} header; pass it as {@code ifNoneMatch} later. */
  public @Nullable String etag() {
    return etag;
  }

  /** The {@code Cache-Control} header, with {@code max-age}. */
  public @Nullable String cacheControl() {
    return cacheControl;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return o instanceof CacheInfo c
        && Objects.equals(etag, c.etag)
        && Objects.equals(cacheControl, c.cacheControl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(etag, cacheControl);
  }

  @Override
  public String toString() {
    return "CacheInfo{etag=" + etag + ", cacheControl=" + cacheControl + "}";
  }
}
