package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Client for the finlight API v3. Thread-safe; one instance can serve many threads and streams.
 *
 * <pre>{@code
 * try (FinlightClient client = new FinlightClient(FinlightConfig.builder(apiKey).build())) {
 *   ArticlePage page = client.articles().search(SearchParams.builder().query("ticker:NVDA").build());
 *   page.articles().forEach(a -> System.out.println(a.title()));
 * }
 * }</pre>
 *
 * <p>REST calls block the calling thread. Interrupting the thread cancels the call: waits end at
 * once, nothing is retried, and the call throws {@link java.util.concurrent.CancellationException}.
 */
public final class FinlightClient implements AutoCloseable {
  /** Version of this SDK. */
  public static final String VERSION = BuildInfo.VERSION;

  /** The payload version this SDK speaks on every channel. */
  public static final int PAYLOAD_VERSION = 3;

  private final FinlightConfig config;
  private final HttpClient http;
  private final boolean ownsHttp;
  private final Transport transport;
  private final Articles articles;
  private final Companies companies;
  private final Sources sources;
  private final Set<StreamEngine> streams = ConcurrentHashMap.newKeySet();

  /** A client with the given configuration. */
  public FinlightClient(FinlightConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    HttpClient custom = config.httpClient();
    this.ownsHttp = custom == null;
    this.http =
        custom != null
            ? custom
            : HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    this.transport = new Transport(config, http);
    this.articles = new Articles(this);
    this.companies = new Companies(this);
    this.sources = new Sources(this);
  }

  /** A client with default settings and the given API key. */
  public FinlightClient(String apiKey) {
    this(FinlightConfig.builder(apiKey).build());
  }

  /** The configuration. */
  public FinlightConfig config() {
    return config;
  }

  /** Article search and lookups. */
  public Articles articles() {
    return articles;
  }

  /** Company lookups (need the {@code entityAccess} entitlement). */
  public Companies companies() {
    return companies;
  }

  /** The source catalog. */
  public Sources sources() {
    return sources;
  }

  /**
   * Opens an enriched stream. The parameters are checked at once; the connection opens in the
   * background. Events and hooks run on one daemon thread of the stream, in order.
   *
   * @param params filters
   * @param listener receives events, hooks and the end of the stream
   * @return a handle to close the stream
   */
  public FinlightStream stream(StreamParams params, StreamListener listener) {
    return stream(params, StreamOptions.defaults(), listener);
  }

  /** Opens an enriched stream with options (takeover). */
  public FinlightStream stream(
      StreamParams params, StreamOptions options, StreamListener listener) {
    Objects.requireNonNull(params, "params");
    return open(params.toJson(), false, options, listener);
  }

  /** Opens a raw stream: articles before enrichment, at {@code <wssUrl>/raw}. */
  public FinlightStream rawStream(RawStreamParams params, StreamListener listener) {
    return rawStream(params, StreamOptions.defaults(), listener);
  }

  /** Opens a raw stream with options (takeover). */
  public FinlightStream rawStream(
      RawStreamParams params, StreamOptions options, StreamListener listener) {
    Objects.requireNonNull(params, "params");
    return open(params.toJson(), true, options, listener);
  }

  private FinlightStream open(
      ObjectNode params, boolean raw, StreamOptions options, StreamListener listener) {
    return open(params, raw, options, listener, new StreamEngine.TestHooks() {});
  }

  FinlightStream open(
      ObjectNode params,
      boolean raw,
      StreamOptions options,
      StreamListener listener,
      StreamEngine.TestHooks hooks) {
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(listener, "listener");
    StreamEngine engine =
        new StreamEngine(config, http, params, raw, options.takeover(), listener, hooks);
    streams.add(engine);
    engine.onEnd(() -> streams.remove(engine));
    engine.start();
    return engine.handle();
  }

  /**
   * Closes every open stream of this client and, when the SDK created it, the HTTP client. A client
   * you passed in is never closed.
   */
  @Override
  public void close() {
    List<StreamEngine> open = List.copyOf(streams);
    for (StreamEngine s : open) {
      s.handle().close();
    }
    for (StreamEngine s : open) {
      try {
        if (!s.handle().awaitTermination(java.time.Duration.ofSeconds(5))) {
          Log.warn("a stream did not end within 5 s of close()");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (ownsHttp) {
      // Java 21+ can shut the client down at once; on 17 its daemon threads end with the client.
      try {
        HttpClient.class.getMethod("shutdownNow").invoke(http);
      } catch (NoSuchMethodException e) {
        Log.debug(() -> "HttpClient.shutdownNow is not available on this Java version");
      } catch (ReflectiveOperationException | RuntimeException e) {
        Log.warn("closing the HTTP client failed", e);
      }
    }
  }

  @Override
  public String toString() {
    return "FinlightClient{" + config + "}";
  }

  Transport.Response get(
      String path, List<Map.Entry<String, String>> query, Map<String, String> headers) {
    return transport.send(new Transport.Request("GET", path, query, null, headers));
  }

  Transport.Response post(String path, ObjectNode body) {
    return transport.send(
        new Transport.Request(
            "POST", path, List.of(), Json.write(body).getBytes(StandardCharsets.UTF_8), Map.of()));
  }

  private static String pathSegment(String value) {
    return Query.encode(value);
  }

  /** Article search and lookups: {@code client.articles()}. */
  public static final class Articles {
    private final FinlightClient client;

    private Articles(FinlightClient client) {
      this.client = client;
    }

    /** One page of results. An empty search sends {@code {}}. */
    public ArticlePage search(SearchParams params) {
      Objects.requireNonNull(params, "params");
      return page(params.toJson());
    }

    /** One page with no filters (sends {@code {}}). */
    public ArticlePage search() {
      return search(SearchParams.builder().build());
    }

    ArticlePage page(ObjectNode body) {
      Transport.Response r = client.post("/v3/articles/search", body);
      return Envelopes.page(Envelopes.parse(r.body()));
    }

    /**
     * Walks the pages lazily, up to {@code maxPages} of the config. The body is frozen now; later
     * pages only add the cursor.
     */
    public SearchAllResult searchAll(SearchParams params) {
      return searchAll(params, SearchAllOptions.defaults());
    }

    /** Walks the pages lazily, up to {@code options.maxPages()} pages. */
    public SearchAllResult searchAll(SearchParams params, SearchAllOptions options) {
      Objects.requireNonNull(params, "params");
      Objects.requireNonNull(options, "options");
      Integer max = options.maxPages();
      return new SearchAllResult(
          this::page, params.toJson(), max == null ? client.config.maxPages() : max);
    }

    /**
     * Looks up an article by its id.
     *
     * @throws NotFoundException when no article has this id (also for articles indexed before ids
     *     existed: use {@link #getByLink})
     */
    public Article get(String id) {
      return get(id, LookupOptions.none());
    }

    /** Looks up an article by its id, with {@code include}. */
    public Article get(String id, LookupOptions options) {
      if (id == null || id.isEmpty()) {
        throw new FinlightValidationException("id", "id must not be empty");
      }
      Objects.requireNonNull(options, "options");
      List<Map.Entry<String, String>> q = new ArrayList<>();
      String include = options.includeQuery();
      if (include != null) {
        q.add(new SimpleEntry<>("include", include));
      }
      Transport.Response r = client.get("/v3/articles/" + pathSegment(id), q, Map.of());
      return Envelopes.article(Envelopes.parse(r.body()));
    }

    /**
     * Looks up an article by its exact link. Reaches every article, old ones included.
     *
     * @throws NotFoundException when no article has this link
     */
    public Article getByLink(String link) {
      return getByLink(link, LookupOptions.none());
    }

    /** Looks up an article by its exact link, with {@code include}. */
    public Article getByLink(String link, LookupOptions options) {
      if (link == null || link.isEmpty()) {
        throw new FinlightValidationException("link", "link must not be empty");
      }
      Objects.requireNonNull(options, "options");
      List<Map.Entry<String, String>> q = new ArrayList<>();
      q.add(new SimpleEntry<>("link", link));
      String include = options.includeQuery();
      if (include != null) {
        q.add(new SimpleEntry<>("include", include));
      }
      Transport.Response r = client.get("/v3/articles/by-link", q, Map.of());
      return Envelopes.article(Envelopes.parse(r.body()));
    }
  }

  /**
   * Company lookups: {@code client.companies()}. They need {@code entityAccess}. Each successful
   * request costs 1 credit of the monthly quota; a 304 revalidation and errors are free.
   */
  public static final class Companies {
    private final FinlightClient client;
    private final Map<Long, CompanyRecord> cache;

    private Companies(FinlightClient client) {
      this.client = client;
      int size = client.config.companyCacheSize();
      this.cache =
          java.util.Collections.synchronizedMap(
              new LinkedHashMap<>(16, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CompanyRecord> eldest) {
                  return size() > size;
                }
              });
    }

    /** Resolves a query to at most 10 companies. There are no pages. Costs 1 credit. */
    public CompanyList search(CompanySearchParams params) {
      Objects.requireNonNull(params, "params");
      List<Map.Entry<String, String>> q = new ArrayList<>();
      q.add(new SimpleEntry<>("q", params.q()));
      if (params.mic() != null) {
        q.add(new SimpleEntry<>("mic", params.mic()));
      }
      return Envelopes.companies(Envelopes.parse(client.get("/v3/companies", q, Map.of()).body()));
    }

    /**
     * Resolves 1 to 10 company ids. Ids that do not exist are left out; order is not kept. Costs 1
     * credit per request, however many ids.
     */
    public CompanyList getMany(Collection<Long> ids) {
      if (ids == null || ids.isEmpty() || ids.size() > 10) {
        throw new FinlightValidationException("ids", "ids takes 1 to 10 company ids");
      }
      List<String> parts = new ArrayList<>();
      for (Long id : ids) {
        if (id == null || id < 1) {
          throw new FinlightValidationException("ids", "ids must be integers of 1 or more");
        }
        parts.add(Long.toString(id));
      }
      List<Map.Entry<String, String>> q =
          List.of(new SimpleEntry<>("ids", String.join(",", parts)));
      return Envelopes.companies(Envelopes.parse(client.get("/v3/companies", q, Map.of()).body()));
    }

    /** Resolves 1 to 10 company ids. */
    public CompanyList getMany(long... ids) {
      List<Long> list = new ArrayList<>();
      for (long id : ids) {
        list.add(id);
      }
      return getMany(list);
    }

    /**
     * One company. The SDK keeps the record with its ETag and revalidates it on the next call; an
     * unchanged record (304) comes from the cache with {@link CacheInfo#notModified()} true. A 200
     * costs 1 credit; a 304 revalidation is free.
     *
     * @throws NotFoundException when the id does not exist
     */
    public CompanyRecord get(long companyId) {
      CompanyRecord r = get(companyId, CompanyGetOptions.none());
      if (r == null) {
        throw new IllegalStateException("unreachable: no ifNoneMatch was sent");
      }
      return r;
    }

    /**
     * One company, with options. With {@link CompanyGetOptions#ifNoneMatch} an unchanged record
     * returns null ("not modified"), never an error. A 200 costs 1 credit; a 304 revalidation is
     * free.
     */
    public @Nullable CompanyRecord get(long companyId, CompanyGetOptions options) {
      if (companyId < 1) {
        throw new FinlightValidationException("companyId", "companyId must be 1 or more");
      }
      Objects.requireNonNull(options, "options");
      String callerTag = options.ifNoneMatch();
      CompanyRecord cached = callerTag == null ? cache.get(companyId) : null;
      String sentTag =
          callerTag != null
              ? callerTag
              : cached != null && cached.cacheInfo() != null ? cached.cacheInfo().etag() : null;
      Map<String, String> headers = sentTag == null ? Map.of() : Map.of("If-None-Match", sentTag);
      Transport.Response r = client.get("/v3/companies/" + companyId, List.of(), headers);
      String etag = r.headers().first("etag");
      String cacheControl = r.headers().first("cache-control");
      if (r.status() == 304) {
        if (callerTag != null || cached == null) {
          return null;
        }
        CompanyRecord fresh =
            cached.withCacheInfo(new CacheInfo(etag != null ? etag : sentTag, cacheControl, true));
        remember(fresh);
        return fresh;
      }
      JsonNode body = Envelopes.parse(r.body());
      CompanyRecord record =
          Envelopes.company(body).withCacheInfo(new CacheInfo(etag, cacheControl, false));
      remember(record);
      return record;
    }

    private void remember(CompanyRecord record) {
      if (client.config.companyCacheSize() > 0
          && record.cacheInfo() != null
          && record.cacheInfo().etag() != null) {
        cache.put(record.companyId(), record);
      }
    }
  }

  /** The source catalog: {@code client.sources()}. */
  public static final class Sources {
    private final FinlightClient client;

    private Sources(FinlightClient client) {
      this.client = client;
    }

    /**
     * Every source you can query, sorted by domain. Costs 1 credit per successful request; errors
     * are free.
     */
    public SourceList list() {
      return Envelopes.sources(
          Envelopes.parse(client.get("/v3/sources", List.of(), Map.of()).body()));
    }
  }
}
