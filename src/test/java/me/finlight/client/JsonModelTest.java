package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import me.finlight.client.internal.FlexTimestamps;
import me.finlight.client.internal.Json;
import me.finlight.client.model.Article;
import me.finlight.client.model.Category;
import me.finlight.client.model.GetArticlesParams;
import me.finlight.client.model.GetArticlesWebSocketParams;
import me.finlight.client.model.OrderBy;
import me.finlight.client.model.SortOrder;
import org.junit.jupiter.api.Test;

class JsonModelTest {

  @Test
  void parsesArticleWithStringConfidenceAndCompanies() throws Exception {
    String json =
        """
        {"link":"https://example.com/1","title":"T","publishDate":"2025-06-01T12:00:00.123Z",
         "source":"example.com","language":"en","confidence":"0.87","sentiment":"negative",
         "companies":[{"companyId":42,"name":"Apple","ticker":"AAPL","confidence":"0.99",
                       "isins":["US0378331005"]}],
         "unknownFutureField":{"nested":true}}""";
    Article article = Json.mapper().readValue(json, Article.class);
    assertEquals(0.87, article.confidence());
    assertEquals(Instant.parse("2025-06-01T12:00:00.123Z"), article.publishDate());
    assertEquals(1, article.companies().size());
    assertEquals(0.99, article.companies().get(0).confidence());
    assertEquals(42, article.companies().get(0).companyId());
    assertEquals(List.of("US0378331005"), article.companies().get(0).isins());
    assertTrue(article.images().isEmpty(), "absent lists become empty");
    assertNull(article.content());
  }

  @Test
  void parsesFlexibleTimestampFormats() {
    assertEquals(
        Instant.parse("2025-06-01T12:00:00Z"), FlexTimestamps.parse("2025-06-01T12:00:00Z"));
    assertEquals(
        Instant.parse("2025-06-01T10:00:00Z"), FlexTimestamps.parse("2025-06-01T12:00:00+02:00"));
    assertEquals(
        Instant.parse("2025-06-01T12:00:00.5Z"), FlexTimestamps.parse("2025-06-01T12:00:00.500"));
    assertEquals(
        Instant.parse("2025-06-01T12:00:00Z"), FlexTimestamps.parse("2025-06-01 12:00:00"));
    assertEquals(Instant.parse("2025-06-01T00:00:00Z"), FlexTimestamps.parse("2025-06-01"));
  }

  @Test
  void serializesParamsOmittingUnsetFields() {
    GetArticlesParams params =
        GetArticlesParams.builder()
            .query("Nvidia")
            .tickers(List.of("NVDA"))
            .categories(List.of(Category.TECHNOLOGY, Category.MARKETS))
            .orderBy(OrderBy.PUBLISH_DATE)
            .order(SortOrder.DESC)
            .includeContent(true)
            .pageSize(20)
            .build();
    JsonNode node = Json.mapper().valueToTree(params);
    assertEquals("Nvidia", node.get("query").asText());
    assertEquals("NVDA", node.get("tickers").get(0).asText());
    assertEquals("technology", node.get("categories").get(0).asText());
    assertEquals("publishDate", node.get("orderBy").asText());
    assertEquals("DESC", node.get("order").asText());
    assertTrue(node.get("includeContent").asBoolean());
    assertEquals(20, node.get("pageSize").asInt());
    assertFalse(node.has("sources"), "unset fields must be omitted");
    assertFalse(node.has("from"), "unset fields must be omitted");
    assertFalse(node.has("page"), "unset fields must be omitted");
  }

  @Test
  void serializesWebSocketParamsOmittingUnsetFields() {
    GetArticlesWebSocketParams params =
        GetArticlesWebSocketParams.builder().tickers(List.of("AAPL")).includeUpdates(true).build();
    JsonNode node = Json.mapper().valueToTree(params);
    assertEquals("AAPL", node.get("tickers").get(0).asText());
    assertTrue(node.get("includeUpdates").asBoolean());
    assertFalse(node.has("query"));
    assertFalse(node.has("extended"));
  }
}
