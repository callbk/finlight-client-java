package me.finlight.client;

import java.util.List;

/** One page of {@code articles().search(...)}. */
public final class ArticlePage {
  private final List<Article> articles;
  private final Pagination pagination;
  private final List<SkippedEntry> skipped;

  ArticlePage(List<Article> articles, Pagination pagination, List<SkippedEntry> skipped) {
    this.articles = List.copyOf(articles);
    this.pagination = pagination;
    this.skipped = List.copyOf(skipped);
  }

  /** The articles that could be read. */
  public List<Article> articles() {
    return articles;
  }

  /** Paging data. */
  public Pagination pagination() {
    return pagination;
  }

  /** Entries that could not be read (logged as a warning). */
  public List<SkippedEntry> skipped() {
    return skipped;
  }

  @Override
  public String toString() {
    return "ArticlePage{articles=" + articles.size() + ", pagination=" + pagination + "}";
  }
}
