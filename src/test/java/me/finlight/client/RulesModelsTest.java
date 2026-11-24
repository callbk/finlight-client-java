package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** R-MODELS-1 to R-MODELS-3 and R-NULL-1 beyond the kit cases. */
class RulesModelsTest {
  private static ObjectNode article() {
    return Kit.load("fixtures/decode/full-article.json").get("wire").deepCopy();
  }

  @Test
  void aBrokenEntryIsSkippedAndKept() throws Exception {
    ObjectNode broken = article();
    broken.remove("link");
    ObjectNode wrongType = article();
    ((ObjectNode) wrongType.get("sentiment")).put("confidence", "0.95");
    ObjectNode body = Kit.MAPPER.createObjectNode();
    ArrayNode list = body.putArray("articles");
    list.add(article()).add(broken).add(wrongType).add(article());
    body.set(
        "pagination",
        Kit.parse("{\"limit\":4,\"returned\":4,\"hasMore\":false,\"nextCursor\":null}"));
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(200, "application/json", body.toString());
      ArticlePage page = client.articles().search();
      assertEquals(2, page.articles().size(), "the other entries are delivered");
      assertEquals(2, page.skipped().size());
      assertEquals(1, page.skipped().get(0).index());
      assertEquals(broken, Kit.parse(page.skipped().get(0).raw()));
      assertTrue(page.skipped().get(0).reason().contains("link"));
      assertTrue(
          page.skipped().get(1).reason().contains("sentiment.confidence"),
          "no coercion of \"0.95\"");
      client.close();
    }
  }

  @Test
  void aSingleLookupWithoutLinkIsADecodeError() throws Exception {
    ObjectNode broken = article();
    broken.remove("link");
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(200, "application/json", "{\"article\":" + broken + "}");
      FinlightDecodeException e =
          assertThrows(FinlightDecodeException.class, () -> client.articles().get("x"));
      assertEquals("article.link", e.path());
      assertEquals(FinlightErrorKind.DECODE, e.kind());
      mock.enqueue(200, "application/json", "not json");
      assertThrows(FinlightDecodeException.class, () -> client.articles().get("x"));
      client.close();
    }
  }

  @Test
  void requiredKeys() {
    for (String key : new String[] {"id", "link", "title"}) {
      ObjectNode a = article();
      a.putNull(key);
      assertThrows(Wire.Failure.class, () -> Article.read(a, ""), key);
    }
    ObjectNode noDomain = article();
    ((ObjectNode) noDomain.get("source")).remove("domain");
    assertThrows(Wire.Failure.class, () -> Article.read(noDomain, ""));
    ObjectNode badCompany = article();
    ((ObjectNode) badCompany.get("companies").get(0)).put("companyId", "900001");
    assertThrows(
        Wire.Failure.class, () -> Article.read(badCompany, ""), "no string-to-number coercion");
    ObjectNode noType = article();
    ((ObjectNode) noType.get("events").get(0)).remove("type");
    assertThrows(Wire.Failure.class, () -> Article.read(noType, ""));
  }

  @Test
  void optionalKeysMayBeMissing() throws Exception {
    ObjectNode a = article();
    for (String k :
        new String[] {
          "summary",
          "language",
          "media",
          "publishedAt",
          "firstSeenAt",
          "revisedAt",
          "availableAt",
          "countries",
          "categories"
        }) {
      a.remove(k);
    }
    ((ObjectNode) a.get("companies").get(0)).remove("role");
    Article x = Article.read(a, "");
    assertNull(x.summary());
    assertEquals(0, x.media().size());
    assertEquals(0, x.countries().size());
    assertNull(x.companies().value().get(0).role());
  }

  @Test
  void valuesArriveAsSent() throws Exception {
    ObjectNode a = article();
    a.put("publishedAt", "now");
    a.put("firstSeenAt", "2026-11-23 21:04:05Z");
    a.put("revisedAt", "2026-11-23T21:04:05");
    a.put("availableAt", "2026-11-23T21:04:05.123+01:00");
    ((ObjectNode) a.get("sentiment")).put("confidence", 0.912345678);
    Article x = Article.read(a, "");
    assertEquals("now", x.publishedAt());
    assertNull(x.publishedAtTime(), "no lenient parsing");
    assertNull(x.firstSeenAtTime());
    assertNull(x.revisedAtTime(), "a time without a zone is unreadable");
    assertEquals(Instant.parse("2026-11-23T20:04:05.123Z"), x.availableAtTime());
    assertEquals(0.912345678, x.sentiment().value().confidence(), 0.0, "no rounding");
    JsonNode frame =
        Kit.parse(
            "{\"event\":\"connection.admitted\",\"payloadVersion\":3,\"data\":{\"leaseId\":\"l\",\"serverNow\":1763932800123,\"clientNonce\":null}}");
    Frames.Admitted admitted = (Frames.Admitted) Frames.decode(frame);
    assertEquals(1763932800123L, admitted.serverNow());
    ObjectNode big = article();
    ((ObjectNode) big.get("companies").get(0)).put("companyId", 9007199254740991L);
    assertEquals(9007199254740991L, Article.read(big, "").companies().value().get(0).companyId());
  }

  @Test
  void absentStaysAbsentWhenWritten() throws Exception {
    ObjectNode a = article();
    a.remove("content");
    a.remove("companies");
    ((ObjectNode) a.get("events").get(0)).remove("companyIds");
    a.putNull("sentiment");
    Article x = Article.read(a, "");
    JsonNode out = Kit.parse(x.toWire());
    assertFalse(out.has("content"));
    assertFalse(out.has("companies"));
    assertFalse(out.get("events").get(0).has("companyIds"));
    assertTrue(out.get("sentiment").isNull(), "null stays null");
    assertEquals(x, Article.read(out, ""));
  }

  @Test
  void unknownKeysEverywhereAreIgnoredAndReachable() throws Exception {
    ObjectNode body =
        (ObjectNode)
            Kit.parse(
                "{\"sources\":[{\"domain\":\"a.example\",\"future\":1,\"languages\":[\"en\"]}],\"meta\":{}}");
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(200, "application/json", body.toString());
      SourceList list = client.sources().list();
      assertEquals(1, Kit.parse(list.get(0).rawJson()).get("future").asInt());
      assertFalse(list.get(0).hasContent().isPresent());
      mock.enqueue(
          400,
          "application/problem+json",
          "{\"code\":\"VALIDATION_FAILED\",\"status\":400,\"future\":true}");
      FinlightApiException e =
          assertThrows(FinlightApiException.class, () -> client.articles().search());
      assertTrue(Kit.parse(e.rawJson()).get("future").asBoolean());
      client.close();
    }
  }

  @Test
  void gatedHasThreeStates() {
    assertFalse(Gated.absent().isPresent());
    assertThrows(java.util.NoSuchElementException.class, () -> Gated.absent().value());
    assertTrue(Gated.of(null).isPresent());
    assertNull(Gated.of(null).value());
    assertEquals("x", Gated.of("x").value());
    assertEquals(4, Gated.of("abcd").map(String::length).value());
    assertFalse(Gated.<String>absent().map(String::length).isPresent());
  }
}
