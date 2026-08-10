package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import me.finlight.client.model.ArticleResponse;
import me.finlight.client.model.GetArticlesParams;
import me.finlight.client.model.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Smoke test against the real finlight API. Skipped unless FINLIGHT_API_KEY is set:
 *
 * <pre>FINLIGHT_API_KEY=sk_... ./gradlew test --tests '*IntegrationSmokeTest'</pre>
 */
@EnabledIfEnvironmentVariable(named = "FINLIGHT_API_KEY", matches = ".+")
class IntegrationSmokeTest {

  private FinlightClient client() {
    FinlightConfig.Builder builder = FinlightConfig.builder(System.getenv("FINLIGHT_API_KEY"));
    String baseUrl = System.getenv("FINLIGHT_BASE_URL");
    if (baseUrl != null && !baseUrl.isBlank()) {
      builder.baseUrl(baseUrl);
    }
    return FinlightClient.create(builder.build());
  }

  @Test
  void fetchesArticles() {
    ArticleResponse response =
        client()
            .articles()
            .fetchArticles(GetArticlesParams.builder().query("nvidia").pageSize(5).build());
    assertFalse(response.articles().isEmpty());
    response
        .articles()
        .forEach(
            article -> {
              assertNotNull(article.link());
              assertNotNull(article.publishDate());
            });
  }

  @Test
  void listsSources() {
    List<Source> sources = client().sources().getSources();
    assertFalse(sources.isEmpty());
  }
}
