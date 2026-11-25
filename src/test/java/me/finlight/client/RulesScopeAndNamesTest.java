package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** R-SCOPE-1 (no v1/v2 left) and R-NAMES-1 (the public names). */
class RulesScopeAndNamesTest {
  private static final List<String> LEGACY =
      List.of(
          "includecontent",
          "include_content",
          "includeentities",
          "include_entities",
          "extended",
          "pagesize",
          "page_size",
          "publishdate",
          "publish_date",
          "isupdate",
          "is_update",
          "/v1/",
          "/v2/",
          "sendarticle",
          "admin_kick");

  /** Only the frame decoder (R-WS-1) and the webhook version check (R-HOOK-2) may know v2. */
  private static final Set<String> ALLOWED = Set.of("Frames.java", "Webhooks.java");

  @Test
  void noLegacyNamesInTheSources() throws IOException {
    List<String> hits = new ArrayList<>();
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String text = Files.readString(p).toLowerCase(Locale.ROOT);
        for (String word : LEGACY) {
          if (text.contains(word) && !ALLOWED.contains(p.getFileName().toString())) {
            hits.add(p + ": " + word);
          }
        }
      }
    }
    assertEquals(List.of(), hits);
  }

  @Test
  void everyRequestTypeWithEveryFieldCarriesNoLegacyKey() {
    SearchParams search =
        SearchParams.builder()
            .query("q")
            .sources("a")
            .optInSources("b")
            .excludeSources("c")
            .sourceTypes(SourceType.NEWSWIRE)
            .excludeSourceTypes("press_release")
            .from(Instant.EPOCH)
            .to("2026-01-01")
            .languages("en")
            .tickers("X")
            .countries("US")
            .categories("c")
            .hasContent(true)
            .roles("acquirer")
            .minRelevance(0.5)
            .eventTypes("mna")
            .eventDirections("positive")
            .minImportance("high")
            .sectors("s")
            .companyCountries("US")
            .exchanges("XNAS")
            .orderBy("availableAt")
            .order("asc")
            .limit(10)
            .cursor("c")
            .include("content", "companies", "events")
            .build();
    StreamParams stream =
        StreamParams.builder()
            .query("q")
            .sources("a")
            .excludeSources("b")
            .optInSources("c")
            .sourceTypes(SourceType.NEWSWIRE)
            .excludeSourceTypes("blog")
            .language("en")
            .tickers("X")
            .countries("US")
            .categories("c")
            .excludeEmptyContent(true)
            .includeUpdates(true)
            .include("content")
            .build();
    RawStreamParams raw =
        RawStreamParams.builder()
            .query("q")
            .sources("a")
            .excludeSources("b")
            .sourceTypes("newswire")
            .excludeSourceTypes(SourceType.BLOG)
            .language("en")
            .includeUpdates(true)
            .build();
    for (String json :
        List.of(
            Json.write(search.toJson()), Json.write(stream.toJson()), Json.write(raw.toJson()))) {
      for (String key :
          List.of(
              "includeContent",
              "includeEntities",
              "extended",
              "pageSize",
              "publishDate",
              "isUpdate")) {
        assertFalse(json.contains("\"" + key + "\""), key + " in " + json);
      }
    }
  }

  /** The public top-level types equal the table in api-v3-sdk-style.md (Java column). */
  @Test
  void publicTypesMatchTheNameTable() throws IOException {
    Set<String> expected =
        new TreeSet<>(
            List.of(
                "FinlightClient",
                "FinlightConfig",
                "Article",
                "ArticleSource",
                "Media",
                "Sentiment",
                "Company",
                "Listing",
                "EventTag",
                "CompanyRecord",
                "Source",
                "Pagination",
                "ArticlePage",
                "SkippedEntry",
                "CompanyList",
                "SourceList",
                "CacheInfo",
                "StreamEvent",
                "StreamEventKind",
                "AdmittedInfo",
                "ReconnectInfo",
                "QuotaWaitInfo",
                "StreamOptions",
                "StreamListener",
                "FinlightStream",
                "WebhookEvent",
                "WrongPayloadVersion",
                "WebhookPayload",
                "WebhookVerifyOptions",
                "Webhooks",
                "FieldIssue",
                "Gated",
                "SearchAllResult",
                "SearchAllOptions",
                "RetryOnBurst",
                // errors
                "FinlightException",
                "FinlightApiException",
                "ValidationFailedException",
                "CursorInvalidException",
                "CursorMismatchException",
                "UnauthorizedException",
                "ForbiddenException",
                "NotFoundException",
                "RateLimitedException",
                "InternalServerException",
                "FinlightValidationException",
                "FinlightNetworkException",
                "FinlightTimeoutException",
                "FinlightDecodeException",
                "WebhookVerificationException",
                "PreemptedException",
                "FinlightErrorKind",
                "ApiErrorKind",
                "WebhookVerificationReason",
                // parameter types
                "SearchParams",
                "LookupOptions",
                "CompanySearchParams",
                "CompanyGetOptions",
                "StreamParams",
                "RawStreamParams"));
    for (String vocab :
        List.of(
            "IncludeBlock",
            "OrderBy",
            "SortOrder",
            "CompanyRole",
            "EventType",
            "EventDirection",
            "Importance",
            "SentimentLabel",
            "SourceType",
            "StreamEventName",
            "ConnectionErrorCode",
            "PreemptReason",
            "WebhookEventName",
            "ProblemCode",
            "RateLimitType",
            "ReconnectReason")) {
      expected.add(vocab);
      expected.add(vocab + "Values");
    }
    Set<String> actual;
    try (Stream<Path> files = Files.list(Path.of("src/main/java/me/finlight/client"))) {
      actual =
          files
              .map(p -> p.getFileName().toString())
              .filter(n -> n.endsWith(".java") && !n.equals("package-info.java"))
              .map(n -> n.substring(0, n.length() - 5))
              .filter(RulesScopeAndNamesTest::isPublic)
              .collect(Collectors.toCollection(TreeSet::new));
    }
    assertEquals(expected, actual);
  }

  private static boolean isPublic(String simpleName) {
    try {
      return Modifier.isPublic(Class.forName("me.finlight.client." + simpleName).getModifiers());
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void versionAndPayloadVersion() {
    assertTrue(FinlightClient.VERSION.matches("\\d+\\.\\d+\\.\\d+.*"));
    assertFalse(FinlightClient.VERSION.startsWith("v"));
    assertEquals(3, FinlightClient.PAYLOAD_VERSION);
    assertEquals("finlight-client-java/" + FinlightClient.VERSION, Transport.USER_AGENT);
  }
}
