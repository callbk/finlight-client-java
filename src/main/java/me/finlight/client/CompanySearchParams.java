package me.finlight.client;

import org.jspecify.annotations.Nullable;

/** Parameters of {@code companies().search(params)}: a query and an optional exchange. */
public final class CompanySearchParams {
  private final String q;
  private final @Nullable String mic;

  private CompanySearchParams(String q, @Nullable String mic) {
    if (q == null || q.isBlank()) {
      throw new FinlightValidationException("q", "q must not be empty");
    }
    if (q.length() > 200) {
      throw new FinlightValidationException("q", "q takes at most 200 characters");
    }
    if (mic != null && mic.length() > 32) {
      throw new FinlightValidationException("mic", "mic takes at most 32 characters");
    }
    this.q = q;
    this.mic = mic;
  }

  /** Ticker, name, alias, ISIN, {@code XNAS:AMD} or a vendor notation; at most 200 characters. */
  public static CompanySearchParams of(String q) {
    return new CompanySearchParams(q, null);
  }

  /**
   * A query narrowed to one exchange.
   *
   * @param q the query, at most 200 characters
   * @param mic a MIC or alias ({@code XNAS}, {@code NASDAQ}), at most 32 characters, or null
   */
  public static CompanySearchParams of(String q, @Nullable String mic) {
    return new CompanySearchParams(q, mic);
  }

  /** The query. */
  public String q() {
    return q;
  }

  /** The exchange, or null. */
  public @Nullable String mic() {
    return mic;
  }

  @Override
  public String toString() {
    return "CompanySearchParams{q=" + q + ", mic=" + mic + "}";
  }
}
