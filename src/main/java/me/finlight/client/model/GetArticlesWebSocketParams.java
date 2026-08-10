package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Stream filter for the enhanced WebSocket stream ({@link
 * me.finlight.client.ArticleWebSocketClient}).
 *
 * <p>Unset fields are omitted; the server applies its documented defaults.
 *
 * @param query advanced query string
 * @param sources limit the stream to these sources (overrides the default source set)
 * @param excludeSources sources to exclude
 * @param optInSources sources to include in addition to the default source set
 * @param language article language (ISO 639-1), server default {@code en}
 * @param extended deprecated, use {@code includeContent}
 * @param tickers filter by company tickers
 * @param includeEntities whether to include tagged company data
 * @param excludeEmptyContent whether to skip articles without content
 * @param includeContent whether to include full article content
 * @param countries filter by country codes (ISO 3166-1 alpha-2)
 * @param categories filter by article categories
 * @param includeUpdates re-deliver articles when they are revised after publication (adds {@code
 *     isUpdate}/{@code revisedDate})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetArticlesWebSocketParams(
    @Nullable String query,
    @Nullable List<String> sources,
    @Nullable List<String> excludeSources,
    @Nullable List<String> optInSources,
    @Nullable String language,
    @Deprecated @Nullable Boolean extended,
    @Nullable List<String> tickers,
    @Nullable Boolean includeEntities,
    @Nullable Boolean excludeEmptyContent,
    @Nullable Boolean includeContent,
    @Nullable List<String> countries,
    @Nullable List<Category> categories,
    @Nullable Boolean includeUpdates) {

  /** Returns a new builder with no fields set. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link GetArticlesWebSocketParams}. */
  public static final class Builder {
    private @Nullable String query;
    private @Nullable List<String> sources;
    private @Nullable List<String> excludeSources;
    private @Nullable List<String> optInSources;
    private @Nullable String language;
    private @Nullable Boolean extended;
    private @Nullable List<String> tickers;
    private @Nullable Boolean includeEntities;
    private @Nullable Boolean excludeEmptyContent;
    private @Nullable Boolean includeContent;
    private @Nullable List<String> countries;
    private @Nullable List<Category> categories;
    private @Nullable Boolean includeUpdates;

    private Builder() {}

    /** Advanced query string with boolean operators and field filters. */
    public Builder query(String query) {
      this.query = query;
      return this;
    }

    /** Limit the stream to these sources (overrides the default source set). */
    public Builder sources(List<String> sources) {
      this.sources = List.copyOf(sources);
      return this;
    }

    /** Sources to exclude. */
    public Builder excludeSources(List<String> excludeSources) {
      this.excludeSources = List.copyOf(excludeSources);
      return this;
    }

    /** Sources to include in addition to the default source set. */
    public Builder optInSources(List<String> optInSources) {
      this.optInSources = List.copyOf(optInSources);
      return this;
    }

    /** Article language (ISO 639-1), server default {@code en}. */
    public Builder language(String language) {
      this.language = language;
      return this;
    }

    /**
     * Whether to include content.
     *
     * @deprecated use {@link #includeContent(boolean)}
     */
    @Deprecated
    public Builder extended(boolean extended) {
      this.extended = extended;
      return this;
    }

    /** Filter by company tickers (e.g. {@code AAPL}, {@code NVDA}). */
    public Builder tickers(List<String> tickers) {
      this.tickers = List.copyOf(tickers);
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

    /** Whether to include full article content. */
    public Builder includeContent(boolean includeContent) {
      this.includeContent = includeContent;
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

    /** Re-deliver articles when they are revised after publication. */
    public Builder includeUpdates(boolean includeUpdates) {
      this.includeUpdates = includeUpdates;
      return this;
    }

    /** Builds the immutable parameter object. */
    public GetArticlesWebSocketParams build() {
      return new GetArticlesWebSocketParams(
          query,
          sources,
          excludeSources,
          optInSources,
          language,
          extended,
          tickers,
          includeEntities,
          excludeEmptyContent,
          includeContent,
          countries,
          categories,
          includeUpdates);
    }
  }
}
