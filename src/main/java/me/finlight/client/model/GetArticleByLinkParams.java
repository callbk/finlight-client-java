package me.finlight.client.model;

import org.jspecify.annotations.Nullable;

/**
 * Parameters for {@link me.finlight.client.ArticleService#fetchArticleByLink}.
 *
 * @param link URL of the article to fetch (required)
 * @param includeContent whether to include full article content
 * @param includeEntities whether to include tagged company data
 */
public record GetArticleByLinkParams(
    String link, @Nullable Boolean includeContent, @Nullable Boolean includeEntities) {

  public GetArticleByLinkParams {
    if (link == null || link.isBlank()) {
      throw new IllegalArgumentException("link is required");
    }
  }

  /** Returns a new builder for the given article link. */
  public static Builder builder(String link) {
    return new Builder(link);
  }

  /** Builder for {@link GetArticleByLinkParams}. */
  public static final class Builder {
    private final String link;
    private @Nullable Boolean includeContent;
    private @Nullable Boolean includeEntities;

    private Builder(String link) {
      this.link = link;
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

    /** Builds the immutable parameter object. */
    public GetArticleByLinkParams build() {
      return new GetArticleByLinkParams(link, includeContent, includeEntities);
    }
  }
}
