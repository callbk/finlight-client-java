package me.finlight.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Parameters of {@code articles().search(...)} and {@code searchAll(...)}. Every key is optional
 * and only the keys you set are sent: the SDK adds no defaults (an empty search sends {@code {}}).
 * Vocabulary values are not checked locally, so new server values work without an SDK update.
 * Shapes are checked at once and throw {@link FinlightValidationException}.
 *
 * <pre>{@code
 * SearchParams params = SearchParams.builder()
 *     .query("ticker:NVDA")
 *     .languages("en")
 *     .include(IncludeBlock.COMPANIES, IncludeBlock.EVENTS)
 *     .limit(10)
 *     .build();
 * }</pre>
 */
public final class SearchParams {
  private final Map<String, Object> values;

  private SearchParams(Map<String, Object> values) {
    this.values = values;
  }

  /** A new builder. */
  public static Builder builder() {
    return new Builder(new LinkedHashMap<>());
  }

  /** A builder with the values of these parameters. */
  public Builder toBuilder() {
    return new Builder(new LinkedHashMap<>(values));
  }

  /** A copy with {@code cursor} set to the {@code nextCursor} of a previous page. */
  public SearchParams withCursor(@Nullable String cursor) {
    return toBuilder().cursor(cursor).build();
  }

  /** The cursor, or null. */
  public @Nullable String cursor() {
    Object c = values.get("cursor");
    return c instanceof String s ? s : null;
  }

  /** The request body: only the keys that were set. */
  ObjectNode toJson() {
    return Params.toJson(values);
  }

  @Override
  public String toString() {
    return "SearchParams" + Json.write(toJson());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return o instanceof SearchParams p && p.toJson().equals(toJson());
  }

  @Override
  public int hashCode() {
    return Objects.hash(toJson());
  }

  /** Builder of {@link SearchParams}. Setting a key to null removes it. */
  public static final class Builder {
    private final Map<String, Object> values;

    private Builder(Map<String, Object> values) {
      this.values = values;
    }

    /** The query language, for example {@code ticker:NVDA AND eventType:mna}. */
    public Builder query(@Nullable String query) {
      Params.set(values, "query", query);
      return this;
    }

    /** Restrict to these domains, for example {@code www.reuters.com}. */
    public Builder sources(@Nullable List<String> values) {
      Params.set(this.values, "sources", values == null ? null : Params.strings("sources", values));
      return this;
    }

    /** Restrict to these domains, for example {@code www.reuters.com}. */
    public Builder sources(String @Nullable ... values) {
      return sources(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Add non-default sources to the default pool. */
    public Builder optInSources(@Nullable List<String> values) {
      Params.set(
          this.values,
          "optInSources",
          values == null ? null : Params.strings("optInSources", values));
      return this;
    }

    /** Add non-default sources to the default pool. */
    public Builder optInSources(String @Nullable ... values) {
      return optInSources(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Leave out these domains. */
    public Builder excludeSources(@Nullable List<String> values) {
      Params.set(
          this.values,
          "excludeSources",
          values == null ? null : Params.strings("excludeSources", values));
      return this;
    }

    /** Leave out these domains. */
    public Builder excludeSources(String @Nullable ... values) {
      return excludeSources(values == null ? null : java.util.Arrays.asList(values));
    }

    /**
     * Keep only articles of sources of these types. Articles of unclassified sources never match.
     * Filters inside the source pool and never adds a source to it.
     */
    public Builder sourceTypes(@Nullable List<String> values) {
      Params.set(
          this.values,
          "sourceTypes",
          values == null ? null : Params.strings("sourceTypes", values));
      return this;
    }

    /**
     * Keep only articles of sources of these types. Articles of unclassified sources never match.
     * Filters inside the source pool and never adds a source to it.
     */
    public Builder sourceTypes(String @Nullable ... values) {
      return sourceTypes(values == null ? null : java.util.Arrays.asList(values));
    }

    /**
     * Keep only articles of sources of these types. Articles of unclassified sources never match.
     * Filters inside the source pool and never adds a source to it.
     */
    public Builder sourceTypes(SourceType... values) {
      return sourceTypes(toStrings(values));
    }

    /** Leave out articles of sources of these types. Unclassified sources are kept. */
    public Builder excludeSourceTypes(@Nullable List<String> values) {
      Params.set(
          this.values,
          "excludeSourceTypes",
          values == null ? null : Params.strings("excludeSourceTypes", values));
      return this;
    }

    /** Leave out articles of sources of these types. Unclassified sources are kept. */
    public Builder excludeSourceTypes(String @Nullable ... values) {
      return excludeSourceTypes(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Leave out articles of sources of these types. Unclassified sources are kept. */
    public Builder excludeSourceTypes(SourceType... values) {
      return excludeSourceTypes(toStrings(values));
    }

    /** ISO 639-1 languages. Must not be empty. The server default is {@code en}. */
    public Builder languages(@Nullable List<String> values) {
      if (values != null && values.isEmpty()) {
        throw new FinlightValidationException("languages", "languages must not be empty");
      }
      Params.set(
          this.values, "languages", values == null ? null : Params.strings("languages", values));
      return this;
    }

    /** ISO 639-1 languages. Must not be empty. The server default is {@code en}. */
    public Builder languages(String @Nullable ... values) {
      return languages(values == null ? null : java.util.Arrays.asList(values));
    }

    /**
     * Tickers: {@code SYMBOL}, {@code SYMBOL@MIC} or vendor notations such as {@code 000660.KS}.
     */
    public Builder tickers(@Nullable List<String> values) {
      Params.set(this.values, "tickers", values == null ? null : Params.strings("tickers", values));
      return this;
    }

    /**
     * Tickers: {@code SYMBOL}, {@code SYMBOL@MIC} or vendor notations such as {@code 000660.KS}.
     */
    public Builder tickers(String @Nullable ... values) {
      return tickers(values == null ? null : java.util.Arrays.asList(values));
    }

    /** ISO 3166-1 alpha-2 countries the story is about. */
    public Builder countries(@Nullable List<String> values) {
      Params.set(
          this.values, "countries", values == null ? null : Params.strings("countries", values));
      return this;
    }

    /** ISO 3166-1 alpha-2 countries the story is about. */
    public Builder countries(String @Nullable ... values) {
      return countries(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Categories (open vocabulary). */
    public Builder categories(@Nullable List<String> values) {
      Params.set(
          this.values, "categories", values == null ? null : Params.strings("categories", values));
      return this;
    }

    /** Categories (open vocabulary). */
    public Builder categories(String @Nullable ... values) {
      return categories(values == null ? null : java.util.Arrays.asList(values));
    }

    /**
     * Company roles, correlated with {@code tickers} and {@code minRelevance} on the same company.
     */
    public Builder roles(@Nullable List<String> values) {
      Params.set(this.values, "roles", values == null ? null : Params.strings("roles", values));
      return this;
    }

    /**
     * Company roles, correlated with {@code tickers} and {@code minRelevance} on the same company.
     */
    public Builder roles(String @Nullable ... values) {
      return roles(values == null ? null : java.util.Arrays.asList(values));
    }

    /**
     * Company roles, correlated with {@code tickers} and {@code minRelevance} on the same company.
     */
    public Builder roles(CompanyRole... values) {
      return roles(toStrings(values));
    }

    /** Event types, correlated with {@code eventDirections} and {@code minImportance}. */
    public Builder eventTypes(@Nullable List<String> values) {
      Params.set(
          this.values, "eventTypes", values == null ? null : Params.strings("eventTypes", values));
      return this;
    }

    /** Event types, correlated with {@code eventDirections} and {@code minImportance}. */
    public Builder eventTypes(String @Nullable ... values) {
      return eventTypes(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Event types, correlated with {@code eventDirections} and {@code minImportance}. */
    public Builder eventTypes(EventType... values) {
      return eventTypes(toStrings(values));
    }

    /** Event directions. */
    public Builder eventDirections(@Nullable List<String> values) {
      Params.set(
          this.values,
          "eventDirections",
          values == null ? null : Params.strings("eventDirections", values));
      return this;
    }

    /** Event directions. */
    public Builder eventDirections(String @Nullable ... values) {
      return eventDirections(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Event directions. */
    public Builder eventDirections(EventDirection... values) {
      return eventDirections(toStrings(values));
    }

    /** GICS sector names. */
    public Builder sectors(@Nullable List<String> values) {
      Params.set(this.values, "sectors", values == null ? null : Params.strings("sectors", values));
      return this;
    }

    /** GICS sector names. */
    public Builder sectors(String @Nullable ... values) {
      return sectors(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Countries where the company is domiciled. */
    public Builder companyCountries(@Nullable List<String> values) {
      Params.set(
          this.values,
          "companyCountries",
          values == null ? null : Params.strings("companyCountries", values));
      return this;
    }

    /** Countries where the company is domiciled. */
    public Builder companyCountries(String @Nullable ... values) {
      return companyCountries(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Primary-listing exchanges (MIC). */
    public Builder exchanges(@Nullable List<String> values) {
      Params.set(
          this.values, "exchanges", values == null ? null : Params.strings("exchanges", values));
      return this;
    }

    /** Primary-listing exchanges (MIC). */
    public Builder exchanges(String @Nullable ... values) {
      return exchanges(values == null ? null : java.util.Arrays.asList(values));
    }

    /** Lower bound on the {@code orderBy} timestamp, sent unchanged. */
    public Builder from(@Nullable String from) {
      Params.set(values, "from", from);
      return this;
    }

    /** Lower bound, sent as UTC ISO 8601 with milliseconds. */
    public Builder from(Instant from) {
      return from(Params.time(Objects.requireNonNull(from, "from")));
    }

    /** Lower bound, sent as UTC ISO 8601 with milliseconds. */
    public Builder from(OffsetDateTime from) {
      return from(Params.time(Objects.requireNonNull(from, "from")));
    }

    /** Lower bound, sent as UTC ISO 8601 with milliseconds. */
    public Builder from(ZonedDateTime from) {
      return from(Params.time(Objects.requireNonNull(from, "from")));
    }

    /** Lower bound, sent as {@code YYYY-MM-DD}. */
    public Builder from(LocalDate from) {
      return from(Params.date(Objects.requireNonNull(from, "from")));
    }

    /** Upper bound on the {@code orderBy} timestamp, sent unchanged. */
    public Builder to(@Nullable String to) {
      Params.set(values, "to", to);
      return this;
    }

    /** Upper bound, sent as UTC ISO 8601 with milliseconds. */
    public Builder to(Instant to) {
      return to(Params.time(Objects.requireNonNull(to, "to")));
    }

    /** Upper bound, sent as UTC ISO 8601 with milliseconds. */
    public Builder to(OffsetDateTime to) {
      return to(Params.time(Objects.requireNonNull(to, "to")));
    }

    /** Upper bound, sent as UTC ISO 8601 with milliseconds. */
    public Builder to(ZonedDateTime to) {
      return to(Params.time(Objects.requireNonNull(to, "to")));
    }

    /** Upper bound, sent as {@code YYYY-MM-DD}. */
    public Builder to(LocalDate to) {
      return to(Params.date(Objects.requireNonNull(to, "to")));
    }

    /** True keeps only articles with content. False is sent as false (it has no effect). */
    public Builder hasContent(@Nullable Boolean hasContent) {
      Params.set(values, "hasContent", hasContent);
      return this;
    }

    /** Minimum relevance of a tagged company, 0 to 1. */
    public Builder minRelevance(@Nullable Double minRelevance) {
      if (minRelevance != null && (minRelevance.isNaN() || minRelevance < 0 || minRelevance > 1)) {
        throw new FinlightValidationException(
            "minRelevance", "minRelevance must be between 0 and 1");
      }
      Params.set(values, "minRelevance", minRelevance);
      return this;
    }

    /**
     * Minimum importance of an event: {@code low}, {@code medium}, {@code high}, {@code critical}.
     */
    public Builder minImportance(@Nullable String minImportance) {
      Params.set(values, "minImportance", minImportance);
      return this;
    }

    /** Minimum importance of an event. */
    public Builder minImportance(Importance minImportance) {
      return minImportance(Objects.requireNonNull(minImportance, "minImportance").value());
    }

    /** The timestamp to sort and filter on. Server default {@code publishedAt}. */
    public Builder orderBy(@Nullable String orderBy) {
      Params.set(values, "orderBy", orderBy);
      return this;
    }

    /** The timestamp to sort and filter on. */
    public Builder orderBy(OrderBy orderBy) {
      return orderBy(Objects.requireNonNull(orderBy, "orderBy").value());
    }

    /** {@code asc} or {@code desc} (lowercase). Server default {@code desc}. */
    public Builder order(@Nullable String order) {
      Params.set(values, "order", order);
      return this;
    }

    /** Sort direction. */
    public Builder order(SortOrder order) {
      return order(Objects.requireNonNull(order, "order").value());
    }

    /** Page size, 1 to 100. Server default 20. */
    public Builder limit(@Nullable Integer limit) {
      if (limit != null && (limit < 1 || limit > 100)) {
        throw new FinlightValidationException("limit", "limit must be between 1 and 100");
      }
      Params.set(values, "limit", limit);
      return this;
    }

    /** The {@code nextCursor} of the previous page. Never build or parse one yourself. */
    public Builder cursor(@Nullable String cursor) {
      Params.set(values, "cursor", cursor);
      return this;
    }

    /** Blocks to add: {@code content}, {@code companies}, {@code events}; at most 3. */
    public Builder include(@Nullable List<String> blocks) {
      Params.set(values, "include", blocks == null ? null : Params.include("include", blocks));
      return this;
    }

    /** Blocks to add: {@code content}, {@code companies}, {@code events}; at most 3. */
    public Builder include(String @Nullable ... blocks) {
      return include(blocks == null ? null : java.util.Arrays.asList(blocks));
    }

    /** Blocks to add; at most 3. */
    public Builder include(IncludeBlock... blocks) {
      return include(toStrings(blocks));
    }

    /** Builds the parameters. */
    public SearchParams build() {
      return new SearchParams(new LinkedHashMap<>(values));
    }

    private static List<String> toStrings(Object @Nullable [] values) {
      if (values == null) {
        throw new FinlightValidationException(null, "values must not be null");
      }
      List<String> out = new ArrayList<>(values.length);
      for (Object v : values) {
        if (v == null) {
          throw new FinlightValidationException(null, "values must not contain null");
        }
        out.add(v.toString());
      }
      return out;
    }
  }
}
