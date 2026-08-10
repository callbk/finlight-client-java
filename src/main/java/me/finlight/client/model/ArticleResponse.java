package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One page of article search results.
 *
 * @param status request status reported by the API
 * @param page page number of this result page
 * @param pageSize number of articles per page
 * @param articles the articles of this page (never null, possibly empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArticleResponse(String status, int page, int pageSize, List<Article> articles) {

  public ArticleResponse {
    articles = articles == null ? List.of() : List.copyOf(articles);
  }
}
