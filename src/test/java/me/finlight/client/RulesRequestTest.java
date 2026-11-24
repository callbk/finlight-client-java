package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** R-REQ-1 to R-REQ-3: only set keys go out, fixed formats, shape checks before any request. */
class RulesRequestTest {
  @Test
  void onlySetKeysAreSentAndFalseAndZeroGoOut() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(ConformanceRestTest.okPage()).enqueue(ConformanceRestTest.okPage());
      client.articles().search();
      assertEquals("{}", mock.requests.get(0).bodyText(), "an empty search sends exactly {}");
      client.articles().search(SearchParams.builder().hasContent(false).minRelevance(0.0).build());
      assertEquals(
          Kit.parse("{\"hasContent\":false,\"minRelevance\":0.0}"), mock.requests.get(1).json());
      client.close();
    }
  }

  @Test
  void streamSourceTypesAreArraysOnlyWhenSet() {
    assertEquals("{}", Json.write(StreamParams.builder().build().toJson()));
    assertEquals("{}", Json.write(RawStreamParams.builder().build().toJson()));
    String expected =
        "{\"sourceTypes\":[\"newswire\",\"financial_media\"],\"excludeSourceTypes\":[\"blog\"]}";
    StreamParams s =
        StreamParams.builder()
            .sourceTypes(SourceType.NEWSWIRE, SourceType.FINANCIAL_MEDIA)
            .excludeSourceTypes(SourceType.BLOG)
            .build();
    assertEquals(expected, Json.write(s.toJson()));
    assertEquals(
        s,
        StreamParams.builder()
            .sourceTypes(List.of("newswire", "financial_media"))
            .excludeSourceTypes("blog")
            .build());
    RawStreamParams r =
        RawStreamParams.builder()
            .sourceTypes("newswire", "financial_media")
            .excludeSourceTypes(SourceType.BLOG)
            .build();
    assertEquals(expected, Json.write(r.toJson()));
    assertEquals(
        "{}",
        Json.write(
            r.toBuilder()
                .sourceTypes((List<String>) null)
                .excludeSourceTypes((List<String>) null)
                .build()
                .toJson()));
    assertThrows(
        FinlightValidationException.class,
        () -> StreamParams.builder().sourceTypes(SourceType.NEWSWIRE, null));
    assertThrows(
        FinlightValidationException.class,
        () -> RawStreamParams.builder().excludeSourceTypes(Collections.singletonList(null)));
  }

  @Test
  void unsettingAKeyRemovesIt() {
    SearchParams p = SearchParams.builder().query("x").limit(5).languages("en").build();
    SearchParams q = p.toBuilder().query(null).limit(null).languages((List<String>) null).build();
    assertEquals("{}", Json.write(q.toJson()));
    assertEquals(
        "{\"query\":\"x\",\"limit\":5,\"languages\":[\"en\"],\"cursor\":\"c\"}",
        Json.write(p.withCursor("c").toJson()));
  }

  @Test
  void datesHaveOneFixedFormat() {
    SearchParams p =
        SearchParams.builder()
            .from(Instant.parse("2026-11-01T00:00:00Z"))
            .to(LocalDate.of(2026, 11, 23))
            .build();
    assertEquals(
        "2026-11-01T00:00:00.000Z", p.toJson().get("from").asText(), "milliseconds always");
    assertEquals("2026-11-23", p.toJson().get("to").asText());
    SearchParams q =
        SearchParams.builder()
            .from(OffsetDateTime.of(2026, 11, 1, 2, 3, 4, 5_000_000, ZoneOffset.ofHours(2)))
            .to("2026-11-23")
            .build();
    assertEquals("2026-11-01T00:03:04.005Z", q.toJson().get("from").asText(), "converted to UTC");
    assertEquals("2026-11-23", q.toJson().get("to").asText(), "a caller string goes out unchanged");
  }

  /** Under German and Turkish locales numbers and dates keep the same format (R-REQ-2). */
  @Test
  void formattingIgnoresTheLocale() {
    Locale saved = Locale.getDefault();
    try {
      for (Locale l : List.of(Locale.GERMANY, Locale.forLanguageTag("tr-TR"))) {
        Locale.setDefault(l);
        SearchParams p =
            SearchParams.builder()
                .minRelevance(0.5)
                .from(Instant.parse("2026-11-01T10:20:30.456Z"))
                .include(IncludeBlock.COMPANIES)
                .build();
        assertEquals(
            "{\"minRelevance\":0.5,\"from\":\"2026-11-01T10:20:30.456Z\",\"include\":[\"companies\"]}",
            Json.write(p.toJson()));
        assertEquals("a%2Cb%20c", Query.encode("a,b c"));
        assertEquals("include", "INCLUDE".toLowerCase(Locale.ROOT));
      }
    } finally {
      Locale.setDefault(saved);
    }
  }

  @Test
  void includeIsOneCommaJoinedQueryValue() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      JsonNode article = Kit.load("fixtures/requests/get-by-id.json").get("response");
      mock.enqueue(article);
      client
          .articles()
          .get("abc", LookupOptions.include(IncludeBlock.CONTENT, IncludeBlock.EVENTS));
      HttpMock.Recorded r = mock.requests.get(0);
      assertEquals(List.of("content,events"), r.query().get("include"));
      assertEquals(1, r.query().size());
      client.close();
    }
  }

  @Test
  void unknownVocabularyValuesGoOutUnchanged() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(ConformanceRestTest.okPage());
      client
          .articles()
          .search(SearchParams.builder().eventTypes("esg_controversy").order("sideways").build());
      assertEquals(
          Kit.parse("{\"eventTypes\":[\"esg_controversy\"],\"order\":\"sideways\"}"),
          mock.requests.get(0).json());
      client.close();
    }
  }

  /** Every shape error throws locally; nothing reaches the server. */
  @Test
  void shapeErrorsFailBeforeAnyRequest() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      List<Executable> bad =
          List.of(
              () -> SearchParams.builder().limit(0),
              () -> SearchParams.builder().limit(101),
              () -> SearchParams.builder().languages(Collections.emptyList()),
              () -> SearchParams.builder().minRelevance(1.5),
              () -> SearchParams.builder().minRelevance(-0.1),
              () -> SearchParams.builder().include("content", "companies", "events", "content"),
              () -> SearchParams.builder().sources("a", null),
              () -> StreamParams.builder().include("content", "companies", "events", "events"),
              () -> LookupOptions.include("a", "b", "c", "d"),
              () -> CompanySearchParams.of(""),
              () -> CompanySearchParams.of("x".repeat(201)),
              () -> CompanySearchParams.of("NVDA", "x".repeat(33)),
              () -> client.companies().getMany(List.of()),
              () -> client.companies().getMany(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
              () -> client.companies().getMany(0),
              () -> client.companies().get(0),
              () -> client.articles().getByLink(""),
              () -> client.articles().get(""),
              () -> SearchAllOptions.maxPages(0),
              () -> FinlightConfig.builder(""),
              () -> FinlightConfig.builder("k").baseUrl("not a url"));
      for (Executable e : bad) {
        FinlightValidationException err = assertThrows(FinlightValidationException.class, e);
        assertEquals(FinlightErrorKind.VALIDATION, err.kind());
      }
      assertEquals(0, mock.requests.size(), "no request was sent");
      FinlightValidationException limit =
          assertThrows(FinlightValidationException.class, () -> SearchParams.builder().limit(0));
      assertEquals("limit", limit.param(), "the error names the key");
      client.close();
    }
  }

  @Test
  void companyIdsAreCommaJoined() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(Kit.load("fixtures/requests/companies-get-many.json").get("response"));
      client.companies().getMany(900001, 900002);
      assertEquals("ids=900001%2C900002", mock.requests.get(0).rawQuery());
      client.close();
    }
  }
}
