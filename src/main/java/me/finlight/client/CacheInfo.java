package me.finlight.client;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** HTTP caching data of a company record from {@code companies().get(id)}. */
public final class CacheInfo {
  private final @Nullable String etag;
  private final @Nullable String cacheControl;
  private final boolean notModified;

  CacheInfo(@Nullable String etag, @Nullable String cacheControl, boolean notModified) {
    this.etag = etag;
    this.cacheControl = cacheControl;
    this.notModified = notModified;
  }

  /** The {@code ETag} header; pass it as {@code ifNoneMatch} later. */
  public @Nullable String etag() {
    return etag;
  }

  /** The {@code Cache-Control} header. */
  public @Nullable String cacheControl() {
    return cacheControl;
  }

  /** True when the server answered 304 and the SDK returned its cached copy. */
  public boolean notModified() {
    return notModified;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return o instanceof CacheInfo c
        && Objects.equals(etag, c.etag)
        && Objects.equals(cacheControl, c.cacheControl)
        && notModified == c.notModified;
  }

  @Override
  public int hashCode() {
    return Objects.hash(etag, cacheControl, notModified);
  }

  @Override
  public String toString() {
    return "CacheInfo{etag=" + etag + ", notModified=" + notModified + "}";
  }
}
