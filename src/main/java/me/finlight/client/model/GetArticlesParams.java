package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Search parameters for {@link me.finlight.client.ArticleService#fetchArticles}.
 *
 * <p>Unset fields are omitted from the request; the server applies its documented defaults. Build
 * instances via {@link #builder()}:
 *
 * <pre>{@code
 * GetArticlesParams params = GetArticlesParams.builder()
 *     .tickers(List.of("AAPL", "NVDA"))
 *     .from("2025-01-01")
 *     .includeContent(true)
 *     .pageSize(20)
 *     .build();
 * }</pre>
 *
 * @param query advanced query string, e.g. {@code (ticker:AAPL OR ticker:NVDA) AND NOT
 *     source:www.reuters.com AND "Elon Musk"}
 * @param source deprecated, use {@code sources}
 * @param sources limit results to these sources (overrides the default source set)
 * @param excludeSources sources to exclude from results
 * @param optInSources sources to include in addition to the default source set
 * @param tickers filter by company tickers (e.g. {@code AAPL}, {@code NVDA})
 * @param from start date, {@code YYYY-MM-DD} or ISO 8601
 * @param to end date, {@code YYYY-MM-DD} or ISO 8601
 * @param language article language (ISO 639-1), server default {@code en}
 * @param includeContent whether to include full article content
 * @param includeEntities whether to include tagged company data
 * @param excludeEmptyContent whether to skip articles without content
 * @param orderBy sort field
 * @param order sort direction
 * @param pageSize results per page (1–100)
 * @param page page number
 * @param countries filter by country codes (ISO 3166-1 alpha-2)
 * @param categories filter by article categories
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetArticlesParams(
    @Nullable String query,
    @Deprecated @Nullable String source,
    @Nullable List<String> sources,
    @Nullable List<String> excludeSources,
    @Nullable List<String> optInSources,
    @Nullable List<String> tickers,
    @Nullable String from,
    @Nullable String to,
    @Nullable String language,
    @Nullable Boolean includeContent,
    @Nullable Boolean includeEntities,
    @Nullable Boolean excludeEmptyContent,
    @Nullable OrderBy orderBy,
    @Nullable SortOrder order,
    @Nullable Integer pageSize,
    @Nullable Integer page,
    @Nullable List<String> countries,
    @Nullable List<Category> categories) {

  /** Returns a new builder with no fields set. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link GetArticlesParams}. */
  public static final class Builder {
    private @Nullable String query;
    private @Nullable String source;
    private @Nullable List<String> sources;
    private @Nullable List<String> excludeSources;
    private @Nullable List<String> optInSources;
    private @Nullable List<String> tickers;
    private @Nullable String from;
    private @Nullable String to;
    private @Nullable String language;
    private @Nullable Boolean includeContent;
    private @Nullable Boolean includeEntities;
    private @Nullable Boolean excludeEmptyContent;
    private @Nullable OrderBy orderBy;
    private @Nullable SortOrder order;
    private @Nullable Integer pageSize;
    private @Nullable Integer page;
    private @Nullable List<String> countries;
    private @Nullable List<Category> categories;

    private Builder() {}

    /** Advanced query string with boolean operators and field filters. */
    public Builder query(String query) {
      this.query = query;
      return this;
    }

    /**
     * Single source filter.
     *
     * @deprecated use {@link #sources(List)}
     */
    @Deprecated
    public Builder source(String source) {
      this.source = source;
      return this;
    }

    /** Limit results to these sources (overrides the default source set). */
    public Builder sources(List<String> sources) {
      this.sources = List.copyOf(sources);
      return this;
    }

    /** Sources to exclude from results. */
    public Builder excludeSources(List<String> excludeSources) {
      this.excludeSources = List.copyOf(excludeSources);
      return this;
    }

    /** Sources to include in addition to the default source set. */
    public Builder optInSources(List<String> optInSources) {
      this.optInSources = List.copyOf(optInSources);
      return this;
    }

    /** Filter by company tickers (e.g. {@code AAPL}, {@code NVDA}). */
    public Builder tickers(List<String> tickers) {
      this.tickers = List.copyOf(tickers);
      return this;
    }

    /** Start date, {@code YYYY-MM-DD} or ISO 8601. */
    public Builder from(String from) {
      this.from = from;
      return this;
    }

    /** End date, {@code YYYY-MM-DD} or ISO 8601. */
    public Builder to(String to) {
      this.to = to;
      return this;
    }

    /** Article language (ISO 639-1), server default {@code en}. */
    public Builder language(String language) {
      this.language = language;
      return this;
    }

    /** Whether to include full article content. */
    public Builder includeContent(boolean includeContent) {
      this.includeContent = includeContent;
      return this;
    }

    /** Whether to include tagged company data. */
    public Builder includeEntities(boolean includeEntities) {
      this.includeEntities = includeEntities;
      return this;
    }

    /** Whether to skip articles without content. */
    public Builder excludeEmptyContent(boolean excludeEmptyContent) {
      this.excludeEmptyContent = excludeEmptyContent;
      return this;
    }

    /** Sort field. */
    public Builder orderBy(OrderBy orderBy) {
      this.orderBy = orderBy;
      return this;
    }

    /** Sort direction. */
    public Builder order(SortOrder order) {
      this.order = order;
      return this;
    }

    /** Results per page (1–100). */
    public Builder pageSize(int pageSize) {
      this.pageSize = pageSize;
      return this;
    }

    /** Page number. */
    public Builder page(int page) {
      this.page = page;
      return this;
    }

    /** Filter by country codes (ISO 3166-1 alpha-2). */
    public Builder countries(List<String> countries) {
      this.countries = List.copyOf(countries);
      return this;
    }

    /** Filter by article categories. */
    public Builder categories(List<Category> categories) {
      this.categories = List.copyOf(categories);
      return this;
    }

    /** Builds the immutable parameter object. */
    public GetArticlesParams build() {
      return new GetArticlesParams(
          query,
          source,
          sources,
          excludeSources,
          optInSources,
          tickers,
          from,
          to,
          language,
          includeContent,
          includeEntities,
          excludeEmptyContent,
          orderBy,
          order,
          pageSize,
          page,
          countries,
          categories);
    }
  }
}
