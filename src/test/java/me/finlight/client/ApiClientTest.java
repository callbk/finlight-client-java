package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import me.finlight.client.model.Article;
import me.finlight.client.model.ArticleResponse;
import me.finlight.client.model.GetArticleByLinkParams;
import me.finlight.client.model.GetArticlesParams;
import me.finlight.client.model.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiClientTest {

  private HttpServer server;
  private FinlightClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    client =
        FinlightClient.create(
            FinlightConfig.builder("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .timeout(Duration.ofSeconds(2))
                .build());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  @Test
  void fetchArticlesPostsParamsAndParsesResponse() {
    List<String> apiKeys = new CopyOnWriteArrayList<>();
    List<String> bodies = new CopyOnWriteArrayList<>();
    server.createContext(
        "/v2/articles",
        exchange -> {
          apiKeys.add(exchange.getRequestHeaders().getFirst("X-API-KEY"));
          bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(
              exchange,
              200,
              """
              {"status":"ok","page":1,"pageSize":20,"articles":[
                {"link":"https://example.com/1","title":"A","publishDate":"2025-06-01 08:30:00",
                 "source":"example.com","language":"en","confidence":"0.9"}]}""");
        });

    ArticleResponse response =
        client
            .articles()
            .fetchArticles(GetArticlesParams.builder().query("Nvidia").pageSize(20).build());

    assertEquals("ok", response.status());
    assertEquals(1, response.articles().size());
    assertEquals(0.9, response.articles().get(0).confidence());
    assertEquals(List.of("test-key"), apiKeys);
    assertTrue(bodies.get(0).contains("\"query\":\"Nvidia\""));
  }

  @Test
  void fetchArticleByLinkSendsQueryParamsAndUnwrapsEnvelope() {
    List<String> queries = new CopyOnWriteArrayList<>();
    server.createContext(
        "/v2/articles/by-link",
        exchange -> {
          queries.add(exchange.getRequestURI().getRawQuery());
          respond(
              exchange,
              200,
              """
              {"article":{"link":"https://example.com/1","title":"A",
               "publishDate":"2025-06-01T08:30:00Z","source":"example.com","language":"en"}}""");
        });

    Article article =
        client
            .articles()
            .fetchArticleByLink(
                GetArticleByLinkParams.builder("https://example.com/1?a=b")
                    .includeContent(true)
                    .build());

    assertEquals("A", article.title());
    assertEquals(1, queries.size());
    assertTrue(queries.get(0).contains("link=https%3A%2F%2Fexample.com%2F1%3Fa%3Db"));
    assertTrue(queries.get(0).contains("includeContent=true"));
  }

  @Test
  void getSourcesParsesList() {
    server.createContext(
        "/v2/sources",
        exchange ->
            respond(
                exchange,
                200,
                """
                [{"domain":"example.com","isDefaultSource":true,"isContentAvailable":true},
                 {"domain":"other.com","isDefaultSource":false}]"""));

    List<Source> sources = client.sources().getSources();
    assertEquals(2, sources.size());
    assertEquals("example.com", sources.get(0).domain());
    assertTrue(sources.get(0).isDefaultSource());
  }

  @Test
  void retriesRetryableStatusThenSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    server.createContext(
        "/v2/sources",
        exchange -> {
          if (attempts.incrementAndGet() < 3) {
            respond(exchange, 503, "{}");
          } else {
            respond(exchange, 200, "[]");
          }
        });

    List<Source> sources = client.sources().getSources();
    assertEquals(0, sources.size());
    assertEquals(3, attempts.get());
  }

  @Test
  void doesNotRetryClientErrors() {
    AtomicInteger attempts = new AtomicInteger();
    server.createContext(
        "/v2/sources",
        exchange -> {
          attempts.incrementAndGet();
          respond(exchange, 400, "{\"message\":\"bad request\"}");
        });

    FinlightApiException e =
        assertThrows(FinlightApiException.class, () -> client.sources().getSources());
    assertEquals(400, e.statusCode());
    assertTrue(e.body().contains("bad request"));
    assertEquals(1, attempts.get());
  }

  @Test
  void throwsAfterExhaustingRetries() {
    AtomicInteger attempts = new AtomicInteger();
    server.createContext(
        "/v2/sources",
        exchange -> {
          attempts.incrementAndGet();
          respond(exchange, 503, "{}");
        });

    FinlightApiException e =
        assertThrows(FinlightApiException.class, () -> client.sources().getSources());
    assertEquals(503, e.statusCode());
    assertEquals(3, attempts.get());
  }
}
