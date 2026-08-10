package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Stream filter for the raw WebSocket stream ({@link
 * me.finlight.client.RawArticleWebSocketClient}).
 *
 * <p>Unset fields are omitted; the server applies its documented defaults.
 *
 * @param query advanced query string
 * @param sources limit the stream to these sources (overrides the default source set)
 * @param excludeSources sources to exclude
 * @param optInSources sources to include in addition to the default source set
 * @param language article language (ISO 639-1), server default {@code en}
 * @param includeUpdates re-deliver articles when they are revised after publication (adds {@code
 *     isUpdate}/{@code revisedDate})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetRawArticlesWebSocketParams(
    @Nullable String query,
    @Nullable List<String> sources,
    @Nullable List<String> excludeSources,
    @Nullable List<String> optInSources,
    @Nullable String language,
    @Nullable Boolean includeUpdates) {

  /** Returns a new builder with no fields set. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link GetRawArticlesWebSocketParams}. */
  public static final class Builder {
    private @Nullable String query;
    private @Nullable List<String> sources;
    private @Nullable List<String> excludeSources;
    private @Nullable List<String> optInSources;
    private @Nullable String language;
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

    /** Re-deliver articles when they are revised after publication. */
    public Builder includeUpdates(boolean includeUpdates) {
      this.includeUpdates = includeUpdates;
      return this;
    }

    /** Builds the immutable parameter object. */
    public GetRawArticlesWebSocketParams build() {
      return new GetRawArticlesWebSocketParams(
          query, sources, excludeSources, optInSources, language, includeUpdates);
    }
  }
}
