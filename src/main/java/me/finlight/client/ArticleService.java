package me.finlight.client;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.Map;
import me.finlight.client.internal.ApiClient;
import me.finlight.client.model.Article;
import me.finlight.client.model.ArticleResponse;
import me.finlight.client.model.GetArticleByLinkParams;
import me.finlight.client.model.GetArticlesParams;

/** Fetches financial news articles. */
public final class ArticleService {

  private final ApiClient api;

  ArticleService(ApiClient api) {
    this.api = api;
  }

  /**
   * Searches articles matching the given parameters and returns one result page.
   *
   * <pre>{@code
   * ArticleResponse response = client.articles().fetchArticles(
   *     GetArticlesParams.builder()
   *         .tickers(List.of("AAPL"))
   *         .from("2025-01-01")
   *         .includeContent(true)
   *         .pageSize(20)
   *         .build());
   * }</pre>
   *
   * @throws FinlightApiException for non-2xx responses (after retries)
   */
  public ArticleResponse fetchArticles(GetArticlesParams params) {
    return api.post("/v2/articles", params, new TypeReference<ArticleResponse>() {});
  }

  /**
   * Fetches a single article by its URL.
   *
   * @throws FinlightApiException for non-2xx responses, e.g. when the article is not found
   */
  public Article fetchArticleByLink(GetArticleByLinkParams params) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("link", params.link());
    if (Boolean.TRUE.equals(params.includeContent())) {
      query.put("includeContent", "true");
    }
    if (Boolean.TRUE.equals(params.includeEntities())) {
      query.put("includeEntities", "true");
    }
    ArticleEnvelope envelope =
        api.get("/v2/articles/by-link", query, new TypeReference<ArticleEnvelope>() {});
    return envelope.article();
  }

  /** Fetches a single article by its URL with default options. */
  public Article fetchArticleByLink(String link) {
    return fetchArticleByLink(GetArticleByLinkParams.builder(link).build());
  }

  private record ArticleEnvelope(Article article) {}
}
