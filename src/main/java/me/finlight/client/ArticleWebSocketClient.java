package me.finlight.client;

import me.finlight.client.model.Article;
import me.finlight.client.model.GetArticlesWebSocketParams;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Streams enriched articles (sentiment, entities, optional content) in real time over WebSocket.
 * Duplicate deliveries are suppressed by article link.
 *
 * <pre>{@code
 * client.webSocket().connect(
 *     GetArticlesWebSocketParams.builder().tickers(List.of("AAPL")).build(),
 *     article -> System.out.println(article.title()));
 * }</pre>
 */
public final class ArticleWebSocketClient
    extends BaseWebSocketClient<Article, GetArticlesWebSocketParams> {

  ArticleWebSocketClient(FinlightConfig config, WebSocketOptions options) {
    super(config, options, Article.class, LoggerFactory.getLogger(ArticleWebSocketClient.class));
  }

  @Override
  String webSocketUrl() {
    return config().wssUrl();
  }

  @Override
  @Nullable String articleId(Article article) {
    return article.link();
  }
}
