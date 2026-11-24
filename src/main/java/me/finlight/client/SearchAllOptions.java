package me.finlight.client;

import org.jspecify.annotations.Nullable;

/** Options of {@code articles().searchAll(params, options)}. */
public final class SearchAllOptions {
  private static final SearchAllOptions DEFAULTS = new SearchAllOptions(null);

  private final @Nullable Integer maxPages;

  private SearchAllOptions(@Nullable Integer maxPages) {
    this.maxPages = maxPages;
  }

  /** The defaults: {@code maxPages} from the config (10 unless changed). */
  public static SearchAllOptions defaults() {
    return DEFAULTS;
  }

  /** Stops after this many pages. Every page costs quota. */
  public static SearchAllOptions maxPages(int maxPages) {
    if (maxPages < 1) {
      throw new FinlightValidationException("maxPages", "maxPages must be 1 or more");
    }
    return new SearchAllOptions(maxPages);
  }

  /** The page limit, or null for the config default. */
  public @Nullable Integer maxPages() {
    return maxPages;
  }
}
