package me.finlight.client;

import me.finlight.client.model.GetRawArticlesWebSocketParams;
import me.finlight.client.model.RawArticle;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Streams raw, unenriched articles with minimal latency over WebSocket — no sentiment, entities, or
 * content.
 *
 * <pre>{@code
 * client.rawWebSocket().connect(
 *     GetRawArticlesWebSocketParams.builder().query("Nvidia").build(),
 *     article -> System.out.println(article.title()));
 * }</pre>
 */
public final class RawArticleWebSocketClient
    extends BaseWebSocketClient<RawArticle, GetRawArticlesWebSocketParams> {

  RawArticleWebSocketClient(FinlightConfig config, WebSocketOptions options) {
    super(
        config,
        options,
        RawArticle.class,
        LoggerFactory.getLogger(RawArticleWebSocketClient.class));
  }

  @Override
  String webSocketUrl() {
    return config().wssUrl() + "/raw";
  }

  @Override
  @Nullable String articleId(RawArticle article) {
    return null; // the raw stream does not deduplicate, matching the sibling clients
  }
}
