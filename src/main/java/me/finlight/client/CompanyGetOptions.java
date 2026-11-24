package me.finlight.client;

import org.jspecify.annotations.Nullable;

/** Options of {@code companies().get(id, options)}. */
public final class CompanyGetOptions {
  private static final CompanyGetOptions NONE = new CompanyGetOptions(null);

  private final @Nullable String ifNoneMatch;

  private CompanyGetOptions(@Nullable String ifNoneMatch) {
    this.ifNoneMatch = ifNoneMatch;
  }

  /** No options: the SDK revalidates its own cached copy. */
  public static CompanyGetOptions none() {
    return NONE;
  }

  /**
   * Conditional request with an ETag you kept ({@link CacheInfo#etag()}). When the record did not
   * change, {@code companies().get} returns null.
   */
  public static CompanyGetOptions ifNoneMatch(String etag) {
    if (etag == null || etag.isEmpty()) {
      throw new FinlightValidationException("ifNoneMatch", "ifNoneMatch must not be empty");
    }
    return new CompanyGetOptions(etag);
  }

  /** The ETag, or null. */
  public @Nullable String ifNoneMatch() {
    return ifNoneMatch;
  }
}
