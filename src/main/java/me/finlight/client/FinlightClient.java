package me.finlight.client;

import me.finlight.client.internal.ApiClient;

/**
 * Entry point to the finlight API.
 *
 * <pre>{@code
 * FinlightClient client = FinlightClient.create("your-api-key");
 *
 * // REST
 * ArticleResponse response = client.articles().fetchArticles(
 *     GetArticlesParams.builder().query("Nvidia").build());
 *
 * // Streaming
 * client.webSocket().connect(
 *     GetArticlesWebSocketParams.builder().tickers(List.of("AAPL")).build(),
 *     article -> System.out.println(article.title()));
 * }</pre>
 *
 * <p>Instances are immutable and safe for concurrent use; create one per API key and share it.
 * Webhook verification is available via the static {@link WebhookService#constructEvent}.
 */
public final class FinlightClient {

  private final FinlightConfig config;
  private final ArticleService articles;
  private final SourceService sources;
  private final ArticleWebSocketClient webSocket;
  private final RawArticleWebSocketClient rawWebSocket;

  private FinlightClient(FinlightConfig config, WebSocketOptions webSocketOptions) {
    this.config = config;
    ApiClient api = new ApiClient(config);
    this.articles = new ArticleService(api);
    this.sources = new SourceService(api);
    this.webSocket = new ArticleWebSocketClient(config, webSocketOptions);
    this.rawWebSocket = new RawArticleWebSocketClient(config, webSocketOptions);
  }

  /** Creates a client with default configuration for the given API key. */
  public static FinlightClient create(String apiKey) {
    return create(FinlightConfig.of(apiKey));
  }

  /** Creates a client with the given configuration. */
  public static FinlightClient create(FinlightConfig config) {
    return create(config, WebSocketOptions.defaults());
  }

  /** Creates a client with the given configuration and WebSocket options. */
  public static FinlightClient create(FinlightConfig config, WebSocketOptions webSocketOptions) {
    return new FinlightClient(config, webSocketOptions);
  }

  /** Article search and lookup. */
  public ArticleService articles() {
    return articles;
  }

  /** Available news sources. */
  public SourceService sources() {
    return sources;
  }

  /** Enhanced real-time article stream. */
  public ArticleWebSocketClient webSocket() {
    return webSocket;
  }

  /** Raw low-latency article stream. */
  public RawArticleWebSocketClient rawWebSocket() {
    return rawWebSocket;
  }

  /** The configuration of this client. */
  public FinlightConfig config() {
    return config;
  }
}
