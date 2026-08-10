package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import me.finlight.client.model.Article;
import org.junit.jupiter.api.Test;

/** Exercises the server-message dispatch of the WebSocket protocol handler. */
class WebSocketMessageTest {

  private static final String ARTICLE_MESSAGE =
      """
      {"action":"sendArticle","data":{"link":"https://example.com/1","title":"Hello",
       "publishDate":"2025-06-01T12:00:00Z","source":"example.com","language":"en",
       "confidence":"0.8"}}""";

  private ArticleWebSocketClient newClient() {
    return new ArticleWebSocketClient(FinlightConfig.of("test-key"), WebSocketOptions.defaults());
  }

  @Test
  void deliversParsedArticles() {
    ArticleWebSocketClient client = newClient();
    List<Article> received = new CopyOnWriteArrayList<>();
    var conn = client.new Conn(received::add);

    conn.handleMessage(ARTICLE_MESSAGE);

    assertEquals(1, received.size());
    assertEquals("Hello", received.get(0).title());
    assertEquals(0.8, received.get(0).confidence());
  }

  @Test
  void suppressesDuplicateArticlesByLink() {
    ArticleWebSocketClient client = newClient();
    List<Article> received = new CopyOnWriteArrayList<>();
    var conn = client.new Conn(received::add);

    conn.handleMessage(ARTICLE_MESSAGE);
    conn.handleMessage(ARTICLE_MESSAGE);

    assertEquals(1, received.size(), "second delivery of the same link must be suppressed");
  }

  @Test
  void survivesMalformedMessagesAndUnknownActions() {
    ArticleWebSocketClient client = newClient();
    List<Article> received = new CopyOnWriteArrayList<>();
    var conn = client.new Conn(received::add);

    conn.handleMessage("not json");
    conn.handleMessage("{\"action\":\"somethingNew\",\"data\":{}}");
    conn.handleMessage("{\"action\":\"pong\",\"t\":123}");
    conn.handleMessage(ARTICLE_MESSAGE);

    assertEquals(1, received.size());
  }

  @Test
  void adminKickClosesConnection() {
    ArticleWebSocketClient client = newClient();
    var conn = client.new Conn(article -> {});

    conn.handleMessage("{\"action\":\"admin_kick\",\"retryAfter\":1000}");

    // Without an established socket the close completes immediately.
    assertTrue(conn.closed.isDone());
    assertEquals(4003, conn.closed.join().code());
  }

  @Test
  void consumerExceptionsDoNotBreakTheStream() {
    ArticleWebSocketClient client = newClient();
    var conn =
        client
        .new Conn(
            article -> {
              throw new RuntimeException("consumer bug");
            });

    conn.handleMessage(ARTICLE_MESSAGE);
    // no exception propagated — the stream keeps running
  }
}
