package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/** Kit cases R-01 to R-12, P-01 to P-06 and E-01 to E-25, against a real local HTTP server. */
class ConformanceRestTest {
  static final Map<String, Executable> CASES = new LinkedHashMap<>();

  static {
    for (String id :
        List.of(
            "R-01", "R-02", "R-03", "R-04", "R-05", "R-06", "R-07", "R-08", "R-09", "R-10",
            "R-11")) {
      CASES.put(id, () -> request(id));
    }
    CASES.put("R-12", ConformanceRestTest::unknownKey);
    CASES.put("P-01", ConformanceRestTest::threePages);
    for (String id : List.of("P-02", "P-03", "P-04", "P-05", "P-06")) {
      CASES.put(id, () -> paginationStep(id));
    }
    for (int i = 1; i <= 25; i++) {
      String id = String.format(java.util.Locale.ROOT, "E-%02d", i);
      CASES.put(id, () -> error(id));
    }
  }

  static Set<String> ids() {
    return CASES.keySet();
  }

  @TestFactory
  Stream<DynamicNode> cases() {
    Map<String, String> titles = Kit.cases();
    return CASES.entrySet().stream()
        .map(e -> DynamicTest.dynamicTest(e.getKey() + " " + titles.get(e.getKey()), e.getValue()));
  }

  // --- R ---------------------------------------------------------------------------------------

  private static void request(String id) throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf(id));
    JsonNode sdk = f.get("sdk");
    JsonNode args = sdk.get("args");
    JsonNode expected = f.get("request");
    String apiKey = expected.at("/headers/X-API-KEY").asText();
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client =
          new FinlightClient(
              FinlightConfig.builder(apiKey).baseUrl(mock.url()).clock(new FakeClock()).build());
      mock.enqueue(f.get("response"));
      Object result =
          switch (sdk.get("method").asText()) {
            case "articles.search" -> client.articles().search(SdkCalls.search(args));
            case "articles.get" -> client.articles().get(args.get("id").asText(), include(args));
            case "articles.getByLink" ->
                client.articles().getByLink(args.get("link").asText(), include(args));
            case "companies.search" ->
                client
                    .companies()
                    .search(
                        CompanySearchParams.of(
                            args.get("q").asText(), args.path("mic").asText(null)));
            case "companies.getMany" -> {
              List<Long> ids = new ArrayList<>();
              args.get("ids").forEach(x -> ids.add(x.asLong()));
              yield client.companies().getMany(ids);
            }
            case "companies.get" ->
                args.has("ifNoneMatch")
                    ? client
                        .companies()
                        .get(
                            args.get("companyId").asLong(),
                            CompanyGetOptions.ifNoneMatch(args.get("ifNoneMatch").asText()))
                    : client.companies().get(args.get("companyId").asLong());
            case "sources.list" -> client.sources().list();
            default -> throw new AssertionError("unknown method " + sdk.get("method"));
          };
      assertEquals(1, mock.requests.size());
      HttpMock.Recorded r = mock.requests.get(0);
      checkRequest(expected, r);
      JsonNode body = f.get("response").get("body");
      switch (id) {
        case "R-01", "R-02", "R-03" -> {
          ArticlePage page = assertInstanceOf(ArticlePage.class, result);
          assertEquals(body.get("articles").size(), page.articles().size());
          assertTrue(page.skipped().isEmpty());
          assertEquals(body.at("/pagination/hasMore").asBoolean(), page.pagination().hasMore());
          if (id.equals("R-01")) {
            assertEquals("application/json", r.header("content-type"));
          }
        }
        case "R-04", "R-05" -> {
          Article a = assertInstanceOf(Article.class, result);
          assertEquals(body.at("/article/id").asText(), a.id());
          assertEquals(body.get("article"), Kit.parse(a.rawJson()));
        }
        case "R-06", "R-07" -> {
          CompanyList list = assertInstanceOf(CompanyList.class, result);
          assertEquals(body.get("companies").size(), list.size());
          assertTrue(list.skipped().isEmpty());
          if (id.equals("R-07")) {
            CompanyRecord priv =
                list.stream().filter(c -> c.primaryListing() == null).findFirst().orElseThrow();
            assertEquals(List.of(), priv.listings());
          }
        }
        case "R-08" -> {
          CompanyRecord c = assertInstanceOf(CompanyRecord.class, result);
          CacheInfo info = c.cacheInfo();
          assertNotNull(info);
          assertEquals(f.at("/response/headers/etag").asText(), info.etag());
          assertEquals(f.at("/response/headers/cache-control").asText(), info.cacheControl());
          assertFalse(info.notModified());
          List<String> mics = c.listings().stream().map(Listing::mic).toList();
          assertEquals(mics.size(), Set.copyOf(mics).size(), "one listing per exchange");
          assertEquals(c.primaryListing().mic(), c.listings().get(0).mic(), "primary first");
          assertEquals(true, c.listings().get(0).active());
        }
        case "R-09" -> {
          assertNull(result, "304 with a caller ETag is 'not modified'");
          ownCacheRevalidation(f);
        }
        case "R-10" -> {
          SourceList list = assertInstanceOf(SourceList.class, result);
          assertTrue(list.stream().allMatch(s -> s.hasContent().isPresent()));
          assertTrue(list.stream().anyMatch(s -> Boolean.TRUE.equals(s.isCustom())));
          assertEquals(body.get("sources").size(), list.size());
        }
        case "R-11" -> {
          SourceList list = assertInstanceOf(SourceList.class, result);
          assertTrue(
              list.stream().noneMatch(s -> s.hasContent().isPresent()), "unknown, not false");
          assertTrue(list.stream().allMatch(s -> s.hasContent().orNull() == null));
        }
        default -> throw new AssertionError(id);
      }
      client.close();
    }
  }

  /** R-09, second half: the SDK revalidates its own cached copy and returns it on 304. */
  private static void ownCacheRevalidation(JsonNode notModified) throws Exception {
    JsonNode ok = Kit.load("fixtures/requests/companies-get.json");
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(ok.get("response")).enqueue(notModified.get("response"));
      CompanyRecord first = client.companies().get(900001);
      CompanyRecord second = client.companies().get(900001);
      assertNull(mock.requests.get(0).header("if-none-match"));
      assertEquals(first.cacheInfo().etag(), mock.requests.get(1).header("if-none-match"));
      assertEquals(first, second);
      assertTrue(second.cacheInfo().notModified());
      client.close();
    }
  }

  private static LookupOptions include(JsonNode args) {
    if (!args.has("include")) {
      return LookupOptions.none();
    }
    List<String> blocks = new ArrayList<>();
    args.get("include").forEach(x -> blocks.add(x.asText()));
    return LookupOptions.include(blocks);
  }

  /** Method, path, decoded query, key header and the parsed JSON body (R-REQ-2). */
  static void checkRequest(JsonNode expected, HttpMock.Recorded r) {
    assertEquals(expected.get("method").asText(), r.method());
    assertEquals(expected.get("path").asText(), r.path());
    String query = expected.path("query").asText("");
    assertEquals(HttpMock.decode(query), r.query(), "decoded query");
    assertFalse(r.rawQuery().contains("include%5B%5D") || r.rawQuery().contains("include[]"));
    JsonNode headers = expected.get("headers");
    for (String h : Kit.fieldNames(headers)) {
      if (!h.equalsIgnoreCase("X-Request-Id")) {
        assertEquals(headers.get(h).asText(), r.header(h), "header " + h);
      }
    }
    assertNotNull(r.header("x-request-id"));
    if (expected.has("body")) {
      assertEquals(expected.get("body"), r.json(), "request body");
    } else {
      assertEquals(0, r.body().length, "no body on GET");
    }
  }

  private static void unknownKey() throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf("R-12"));
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(f.get("response"));
      // Java rejects the unknown key before anything runs: the builder has no such method, so the
      // call does not compile. The reflective mapper sees the same.
      assertThrows(SdkCalls.NotExpressible.class, () -> SdkCalls.search(f.at("/sdk/args")));
      assertTrue(
          Arrays.stream(SearchParams.Builder.class.getMethods())
              .noneMatch(m -> m.getName().equals("eventType")));
      assertEquals(0, mock.requests.size(), "nothing was sent");
      client.close();
    }
  }

  // --- P ---------------------------------------------------------------------------------------

  private static void threePages() throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf("P-01"));
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      for (JsonNode step : f.get("steps")) {
        mock.enqueue(step.get("response"));
      }
      // A caller's list that changes after the call must not change later pages.
      List<String> sources = new ArrayList<>();
      f.at("/sdk/args/sources").forEach(x -> sources.add(x.asText()));
      SearchParams params =
          SearchParams.builder().sources(sources).limit(f.at("/sdk/args/limit").asInt()).build();
      SearchAllResult all = client.articles().searchAll(params);
      sources.add("injected.example.com");
      List<String> ids = new ArrayList<>();
      for (Article a : all) {
        ids.add(a.id());
      }
      assertEquals(5, ids.size(), "five articles in order");
      List<String> expectedIds = new ArrayList<>();
      f.get("steps")
          .forEach(
              s ->
                  s.at("/response/body/articles")
                      .forEach(a -> expectedIds.add(a.get("id").asText())));
      assertEquals(expectedIds, ids);
      assertEquals(3, mock.requests.size(), "stops after page 3");
      assertEquals(3, all.pagesFetched());
      assertFalse(all.truncated());
      assertNull(all.nextCursor());
      String page1 = mock.requests.get(0).bodyText();
      for (int i = 0; i < 3; i++) {
        checkRequest(f.get("steps").get(i).get("request"), mock.requests.get(i));
        if (i > 0) {
          String cursor =
              f.get("steps").get(i - 1).at("/response/body/pagination/nextCursor").asText();
          assertEquals(
              page1.substring(0, page1.length() - 1) + ",\"cursor\":\"" + cursor + "\"}",
              mock.requests.get(i).bodyText(),
              "page " + (i + 1) + " is byte-equal to page 1 apart from the cursor");
        }
      }
      List<String> requestIds =
          mock.requests.stream().map(r -> r.header("x-request-id")).distinct().toList();
      assertEquals(3, requestIds.size(), "one X-Request-Id per page");
      assertThrows(FinlightValidationException.class, all::iterator, "a second pass throws");
      assertThrows(FinlightValidationException.class, all::pages);
      client.close();
    }
  }

  private static void paginationStep(String id) throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf(id));
    JsonNode body = f.at("/request/body");
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(f.get("response"));
      int status = f.at("/response/status").asInt();
      if (status == 200) {
        ArticlePage page = client.articles().search(SdkCalls.search(body));
        assertEquals(f.at("/response/body/articles").size(), page.articles().size());
      } else {
        CursorMismatchException e =
            assertThrows(
                CursorMismatchException.class,
                () -> client.articles().search(SdkCalls.search(body)));
        assertEquals("cursor", e.errors().get(0).field());
        if (id.equals("P-05")) {
          assertTrue(
              e.detail().contains("publishedAt:desc") && e.detail().contains("publishedAt:asc"));
        }
      }
      assertEquals(1, mock.requests.size());
      checkRequest(f.get("request"), mock.requests.get(0));
      client.close();
    }
  }

  // --- E ---------------------------------------------------------------------------------------

  private static void error(String id) throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf(id));
    JsonNode req = f.get("request");
    JsonNode expect = f.get("expect");
    String retry = expect.get("retry").asText();
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient client = Support.client(mock, clock);
      for (int i = 0; i < 6; i++) {
        mock.enqueue(f.get("response"));
      }
      Call call = callFor(client, req, mock);
      FinlightApiException err = assertThrows(FinlightApiException.class, call::run);
      checkError(f, err, mock);
      int attempts = mock.requests.size();
      Set<String> requestIds = new java.util.HashSet<>();
      mock.requests.forEach(r -> requestIds.add(r.header("x-request-id")));
      assertEquals(1, requestIds.size(), "one X-Request-Id, kept on retries");
      switch (retry) {
        case "never" -> {
          assertEquals(1, attempts, "never retried");
          assertTrue(clock.sleeps.isEmpty(), "never waited");
        }
        case "backoff" -> {
          assertEquals(4, attempts, "first attempt plus 3 retries");
          assertEquals(3, clock.sleeps.size());
          for (int i = 0; i < 3; i++) {
            long max = Math.min(8000, 500L << i);
            long ms = clock.sleeps.get(i).toMillis();
            assertTrue(ms >= max / 2 && ms <= max, "jittered backoff " + ms);
          }
        }
        case "once-after-burst-wait" -> {
          assertEquals(2, attempts, "one retry after the burst wait");
          assertEquals(List.of(Duration.ofSeconds(7)), clock.sleeps, "exactly Retry-After");
          burstVariants(f);
        }
        default -> throw new AssertionError(retry);
      }
      if (id.equals("E-18") || id.equals("E-10") || id.equals("E-25")) {
        pagerStops(f);
      }
      client.close();
    }
  }

  private interface Call {
    void run();
  }

  /**
   * The SDK call that produces the fixture's request, or a stand-in when the SDK cannot send it.
   */
  private static Call callFor(FinlightClient client, JsonNode req, HttpMock mock) {
    String method = req.get("method").asText();
    String path = req.get("path").asText();
    Map<String, List<String>> query = HttpMock.decode(req.path("query").asText(""));
    Call fallback = () -> client.articles().search();
    if (method.equals("POST") && path.equals("/v3/articles/search")) {
      if (req.has("rawBody")) {
        return fallback; // a broken body no SDK sends
      }
      try {
        SearchParams p = SdkCalls.search(req.get("body"));
        return () -> {
          client.articles().search(p);
          throw new AssertionError("no error");
        };
      } catch (FinlightValidationException e) {
        assertEquals(0, mock.requests.size(), "rejected locally before any request");
        return fallback;
      }
    }
    if (path.equals("/v3/articles/by-link")) {
      return () -> client.articles().getByLink(query.get("link").get(0), lookup(query));
    }
    if (path.startsWith("/v3/articles/")) {
      return () -> client.articles().get(path.substring("/v3/articles/".length()), lookup(query));
    }
    if (path.equals("/v3/companies")) {
      if (query.containsKey("q") && query.containsKey("ids")) {
        return fallback; // q and ids together cannot be expressed
      }
      if (query.containsKey("q")) {
        return () -> client.companies().search(CompanySearchParams.of(query.get("q").get(0)));
      }
      if (query.containsKey("ids")) {
        List<Long> ids = new ArrayList<>();
        for (String s : query.get("ids").get(0).split(",")) {
          if (!s.matches("\\d+")) {
            return fallback; // a non-integer id cannot be expressed
          }
          ids.add(Long.parseLong(s));
        }
        return () -> client.companies().getMany(ids);
      }
      assertThrows(FinlightValidationException.class, () -> CompanySearchParams.of(""));
      assertThrows(FinlightValidationException.class, () -> client.companies().getMany(List.of()));
      assertEquals(0, mock.requests.size());
      return fallback;
    }
    if (path.startsWith("/v3/companies/")) {
      String idText = path.substring("/v3/companies/".length());
      if (!idText.matches("\\d+")) {
        return fallback;
      }
      return () -> client.companies().get(Long.parseLong(idText));
    }
    return fallback;
  }

  private static LookupOptions lookup(Map<String, List<String>> query) {
    return query.containsKey("include")
        ? LookupOptions.include(Arrays.asList(query.get("include").get(0).split(",")))
        : LookupOptions.none();
  }

  private static void checkError(JsonNode f, FinlightApiException err, HttpMock mock) {
    JsonNode expect = f.get("expect");
    JsonNode response = f.get("response");
    JsonNode body = response.get("body");
    ApiErrorKind kind = err.apiKind();
    assertEquals(expect.get("apiKind").asText(), kind.value());
    assertSame(ErrorChecks.classFor(kind), err.getClass());
    assertEquals(FinlightErrorKind.API, err.kind());
    assertEquals(response.get("status").asInt(), err.status());
    JsonNode code = expect.get("code");
    if (code.isNull() || code.asText().equals("NESTJS_FALLBACK")) {
      assertNull(err.code());
    } else {
      assertEquals(code.asText(), err.code().value());
    }
    String sent = mock.requests.get(0).header("x-request-id");
    if (body.isObject() && body.has("requestId")) {
      assertEquals(body.get("requestId").asText(), err.requestId());
    } else {
      assertEquals(sent, err.requestId(), "the SDK fills in the X-Request-Id it sent");
    }
    if (body.isObject() && body.has("errors")) {
      assertEquals(body.get("errors").size(), err.errors().size());
      for (int i = 0; i < body.get("errors").size(); i++) {
        JsonNode e = body.get("errors").get(i);
        assertEquals(e.get("field").asText(), err.errors().get(i).field());
        assertEquals(e.get("issue").asText(), err.errors().get(i).issue());
        List<String> allowed = new ArrayList<>();
        e.path("allowedValues").forEach(x -> allowed.add(x.asText()));
        assertEquals(allowed, err.errors().get(i).allowedValues());
      }
    }
    if (body.isObject() && body.has("detail")) {
      assertEquals(body.get("detail").asText(), err.detail());
    }
    if (body.isTextual()) {
      assertEquals(body.asText(), err.raw(), "non-JSON text kept");
    } else {
      assertEquals(body, Kit.parse(err.raw()));
    }
    if (body.isObject() && body.has("message") && body.get("message").isTextual()) {
      assertEquals(body.get("message").asText(), err.detail());
    }
    if (err instanceof RateLimitedException rl) {
      assertEquals(body.get("limitType").asText(), rl.limitType().value());
      String header = response.at("/headers/retry-after").asText(null);
      assertEquals(
          header == null ? null : Duration.ofSeconds(Long.parseLong(header)), rl.retryAfter());
    }
    assertFalse(err.getMessage().contains(Kit.API_KEY));
  }

  /** E-19 with the retry switched off and without a Retry-After header. */
  private static void burstVariants(JsonNode f) throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient off = Support.client(mock, clock, b -> b.retryOnBurst(false));
      mock.enqueue(f.get("response")).enqueue(f.get("response"));
      assertThrows(RateLimitedException.class, () -> off.articles().search());
      assertEquals(1, mock.requests.size(), "off: thrown at once");
      assertTrue(clock.sleeps.isEmpty());
      off.close();
    }
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient client = Support.client(mock, clock);
      com.fasterxml.jackson.databind.node.ObjectNode noHeader = f.get("response").deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) noHeader.get("headers"))
          .remove("retry-after");
      mock.enqueue(noHeader).enqueue(f.at("/steps").isMissingNode() ? okPage() : okPage());
      ArticlePage page = client.articles().search();
      assertEquals(0, page.articles().size());
      assertEquals(List.of(Duration.ofSeconds(10)), clock.sleeps, "10 s without Retry-After");
      assertEquals(2, mock.requests.size());
      assertEquals(
          mock.requests.get(0).header("x-request-id"), mock.requests.get(1).header("x-request-id"));
      client.close();
    }
  }

  static JsonNode okPage() {
    return Kit.parse(
        "{\"status\":200,\"headers\":{\"content-type\":\"application/json\"},\"body\":{\"articles\":[],"
            + "\"pagination\":{\"limit\":20,\"returned\":0,\"hasMore\":false,\"nextCursor\":null}}}");
  }

  /** The pager stops with the error and never retries or waits. */
  private static void pagerStops(JsonNode f) throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient client = Support.client(mock, clock);
      mock.enqueue(f.get("response")).enqueue(f.get("response"));
      SearchAllResult all = client.articles().searchAll(SearchParams.builder().build());
      Iterator<Article> it = all.iterator();
      assertThrows(FinlightApiException.class, it::hasNext);
      assertFalse(it.hasNext(), "the pager stopped");
      assertEquals(1, mock.requests.size());
      assertTrue(clock.sleeps.isEmpty(), "a monthly 429 is never waited on");
      client.close();
    }
  }

  static Consumer<FinlightConfig.Builder> none() {
    return b -> {};
  }

  static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
