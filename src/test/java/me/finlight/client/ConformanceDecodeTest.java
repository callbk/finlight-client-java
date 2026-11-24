package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/** Kit cases D-01 to D-11 and F-01 to F-04. */
class ConformanceDecodeTest {
  static final Map<String, Executable> CASES = new LinkedHashMap<>();

  static {
    CASES.put(
        "D-01",
        () -> {
          Article a = decodeCase("D-01");
          assertEquals(3, a.media().size(), "media capped at 3");
          assertTrue(a.revisedAt() != null && a.revisedAtTime() != null);
          assertNull(a.companies().value().get(1).primaryListing(), "private company");
          assertTrue(
              a.contentPresent()
                  && a.sentimentPresent()
                  && a.companiesPresent()
                  && a.eventsPresent());
          assertEquals(List.of(900001L, 900002L), a.events().value().get(0).companyIds().value());
        });
    CASES.put(
        "D-02",
        () -> {
          Article a = decodeCase("D-02");
          assertNull(a.availableAt());
          assertFalse(
              a.contentPresent()
                  || a.sentimentPresent()
                  || a.companiesPresent()
                  || a.eventsPresent());
        });
    CASES.put(
        "D-03",
        () -> {
          Article a = decodeCase("D-03");
          assertTrue(a.sentimentPresent());
          assertFalse(a.contentPresent() || a.companiesPresent() || a.eventsPresent());
        });
    CASES.put(
        "D-04",
        () -> {
          Article a = decodeCase("D-04");
          assertSame(Gated.absent(), a.content());
          assertNull(a.companies().orNull(), "absent is never coerced to []");
          assertFalse(
              a.contentPresent()
                  || a.sentimentPresent()
                  || a.companiesPresent()
                  || a.eventsPresent());
        });
    CASES.put(
        "D-05",
        () -> {
          Article a = decodeCase("D-05");
          assertTrue(a.eventsPresent());
          assertFalse(a.events().value().get(0).companyIds().isPresent());
          assertNull(a.events().value().get(0).companyIds().orNull());
        });
    CASES.put(
        "D-06",
        () -> {
          Article a = decodeCase("D-06");
          assertFalse(a.companiesPresent());
          assertTrue(a.eventsPresent());
          assertFalse(a.events().value().get(0).companyIds().isPresent());
        });
    CASES.put(
        "D-07",
        () -> {
          Article a = decodeCase("D-07");
          assertEquals("1970-01-01T00:00:00.000Z", a.publishedAt());
          assertEquals(Instant.EPOCH, a.publishedAtTime());
          assertNull(a.summary());
          assertNull(a.firstSeenAt());
          assertNull(a.firstSeenAtTime());
          assertTrue(a.sentiment().isPresent());
          assertNull(a.sentiment().value());
          Company c = a.companies().value().get(0);
          assertNull(c.role());
          assertNull(c.relevance());
          assertEquals("LGCY", c.primaryListing().ticker());
          assertEquals(List.of(), a.events().value());
          assertTrue(a.content().isPresent());
          assertNull(a.content().value());
          assertEquals(List.of(), a.countries());
          assertEquals(List.of(), a.categories());
        });
    CASES.put(
        "D-08",
        () -> {
          JsonNode f = Kit.load(Kit.fixtureOf("D-08"));
          Article a = decodeCase("D-08");
          assertEquals(f.get("wire").get("title").asText(), a.title());
          assertEquals(f.get("wire").get("summary").asText(), a.summary());
        });
    CASES.put(
        "D-09",
        () -> {
          Article a = decodeCase("D-09");
          assertNull(a.sentiment().value().label());
          assertTrue(a.sentiment().value().confidence() != null);
          assertEquals(List.of(), a.companies().value(), "tagged, no company");
          EventTag e = a.events().value().get(0);
          assertNull(e.direction());
          assertNull(e.importance());
          assertNull(e.confidence());
        });
    CASES.put(
        "D-10",
        () -> {
          JsonNode f = Kit.load(Kit.fixtureOf("D-10"));
          assertEquals(f.get("count").asInt(), f.get("cases").size());
          for (JsonNode sub : f.get("cases")) {
            Article a = Article.read(sub.get("wire"), "");
            checkPresence(a, sub, sub.get("id").asText());
          }
        });
    CASES.put(
        "D-11",
        () -> {
          Article a = decodeCase("D-11");
          assertNull(a.summary());
          assertTrue(a.contentPresent());
          assertNull(a.content().value());
          assertFalse(a.title().isEmpty());
        });
    CASES.put(
        "F-01",
        () -> {
          JsonNode f = Kit.load(Kit.fixtureOf("F-01"));
          Article wire = Article.read(f.get("wire"), "");
          Article known = Article.read(f.get("known"), "");
          assertEquals(known, wire, "every known field equals known");
          assertEquals(known.toWire(), wire.toWire());
          JsonNode raw = Kit.parse(wire.rawJson());
          assertEquals(
              512, raw.get("wordCount").asInt(), "unknown keys stay reachable through rawJson()");
          assertEquals("tender_offer", raw.at("/events/0/subtype").asText());
          assertEquals("BBG000SYNTH1", raw.at("/companies/0/primaryListing/figi").asText());
        });
    CASES.put(
        "F-02",
        () -> {
          JsonNode f = Kit.load(Kit.fixtureOf("F-02"));
          Article a = Article.read(f.get("wire"), "");
          assertEquals("wire_service", a.source().type().value());
          assertFalse(a.source().type().isKnown());
          assertEquals("mixed", a.sentiment().value().label().value());
          assertEquals("guarantor", a.companies().value().get(0).role().value());
          assertEquals(CompanyRole.TARGET, a.companies().value().get(1).role());
          EventTag e = a.events().value().get(0);
          assertEquals("esg_controversy", e.type().value());
          assertEquals("mixed", e.direction().value());
          assertEquals("extreme", e.importance().value());
          assertEquals("synthetic-new-category", a.categories().get(1));
          assertEquals(Kit.parse(a.toWire()), f.get("wire"), "values are kept as sent");
        });
    CASES.put("F-03", ConformanceDecodeTest::envelopes);
    CASES.put("F-04", ConformanceDecodeTest::unknownEventsAndCodes);
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

  @TestFactory
  Stream<DynamicNode> matrixSubCases() {
    JsonNode f = Kit.load(Kit.fixtureOf("D-10"));
    List<DynamicNode> out = new ArrayList<>();
    for (JsonNode sub : f.get("cases")) {
      out.add(
          DynamicTest.dynamicTest(
              sub.get("id").asText(),
              () -> checkPresence(Article.read(sub.get("wire"), ""), sub, sub.get("id").asText())));
    }
    return Stream.of(DynamicContainer.dynamicContainer("D-10 sub-cases", out));
  }

  /** Decodes a single-article decode fixture and checks its presence view and the round trip. */
  static Article decodeCase(String id) throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf(id));
    Article a = Article.read(f.get("wire"), "");
    checkPresence(a, f, id);
    return a;
  }

  static void checkPresence(Article a, JsonNode fixture, String id) {
    JsonNode presence = fixture.get("expect").get("presence");
    for (String path : Kit.fieldNames(presence)) {
      assertEquals(presence.get(path).asText(), Presence.of(a, path), id + " " + path);
    }
    assertEquals(!"absent".equals(presence.get("content").asText()), a.contentPresent(), id);
    assertEquals(!"absent".equals(presence.get("sentiment").asText()), a.sentimentPresent(), id);
    assertEquals(!"absent".equals(presence.get("companies").asText()), a.companiesPresent(), id);
    assertEquals(!"absent".equals(presence.get("events").asText()), a.eventsPresent(), id);
    // R-NULL-1: writing the model back keeps absent absent and null null.
    assertEquals(fixture.get("wire"), Kit.parse(a.toWire()), id + " toWire round trip");
    assertEquals(fixture.get("wire"), Kit.parse(a.rawJson()), id + " rawJson");
  }

  private static void envelopes() throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf("F-03"));
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient client = Support.client(mock, clock);
      for (JsonNode env : f.get("envelopes")) {
        String kind = env.get("kind").asText();
        JsonNode wire = env.get("wire");
        switch (kind) {
          case "rest.search" -> {
            mock.enqueue(200, "application/json", wire.toString());
            ArticlePage page = client.articles().search();
            assertEquals(1, page.articles().size());
            assertFalse(page.pagination().hasMore());
            assertNull(page.pagination().nextCursor());
          }
          case "rest.article" -> {
            mock.enqueue(200, "application/json", wire.toString());
            Article a = client.articles().get(wire.get("article").get("id").asText());
            assertEquals(wire.get("article").get("link").asText(), a.link());
          }
          case "webhook" -> {
            WebhookEvent e = assertInstanceOf(WebhookEvent.class, Support.signedEvent(wire));
            assertEquals(wire.get("eventId").asText(), e.eventId());
            assertTrue(Kit.parse(e.rawJson()).has("deliveryAttempt"));
          }
          case "ws.article" -> {
            Frames.ArticleFrame a =
                assertInstanceOf(Frames.ArticleFrame.class, Frames.decode(wire));
            assertEquals(StreamEventKind.CREATED, a.kind());
            assertEquals(wire.at("/data/id").asText(), Article.read(a.data(), "data").id());
          }
          case "ws.control" -> assertInstanceOf(Frames.Pong.class, Frames.decode(wire));
          default -> throw new AssertionError("unknown envelope " + kind);
        }
      }
      client.close();
    }
  }

  private static void unknownEventsAndCodes() throws Exception {
    JsonNode f = Kit.load(Kit.fixtureOf("F-04"));
    try (HttpMock mock = new HttpMock()) {
      FakeClock clock = new FakeClock();
      FinlightClient client = Support.client(mock, clock);
      for (JsonNode item : f.get("items")) {
        String kind = item.get("kind").asText();
        if ("webhook.event".equals(kind)) {
          WebhookEvent e =
              assertInstanceOf(WebhookEvent.class, Support.signedEvent(item.get("wire")));
          assertEquals("article.retracted", e.event().value());
          assertFalse(e.event().isKnown());
          continue;
        }
        mock.reset();
        com.fasterxml.jackson.databind.node.ObjectNode resp = Kit.MAPPER.createObjectNode();
        resp.put("status", item.get("status").asInt());
        resp.set("headers", item.get("headers"));
        resp.set("body", item.get("wire"));
        mock.enqueue(resp).enqueue(resp);
        FinlightApiException err =
            assertThrows(FinlightApiException.class, () -> client.articles().search());
        assertEquals(1, mock.requests.size(), kind + ": never retried");
        assertEquals(item.get("status").asInt(), err.status());
        assertEquals("req_synthetic", err.requestId());
        assertEquals(item.at("/wire/detail").asText(), err.detail());
        if ("problem.code".equals(kind)) {
          assertSame(FinlightApiException.class, err.getClass(), "no subclass for an unknown code");
          assertEquals(ApiErrorKind.OTHER, err.apiKind());
          assertEquals("PAYMENT_REQUIRED", err.code().value());
        } else {
          RateLimitedException rl = assertInstanceOf(RateLimitedException.class, err);
          assertEquals("daily", rl.limitType().value());
          assertFalse(rl.limitType().isKnown());
        }
      }
      assertTrue(clock.sleeps.isEmpty(), "nothing waited");
      client.close();
    }
  }
}
