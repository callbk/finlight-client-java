package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/** Kit cases W-01 to W-16. */
class ConformanceWebhookTest {
  static final Map<String, Executable> CASES = new LinkedHashMap<>();

  static {
    for (int i = 1; i <= 16; i++) {
      String id = String.format(java.util.Locale.ROOT, "W-%02d", i);
      CASES.put(id, () -> run(id));
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

  private static void run(String id) {
    JsonNode f = Kit.load(Kit.fixtureOf(id));
    String secret = f.get("secret").asText();
    JsonNode req = f.get("request");
    byte[] body = Base64.getDecoder().decode(req.get("rawBodyBase64").asText());
    assertArrayEquals(req.get("rawBody").asText().getBytes(StandardCharsets.UTF_8), body);
    Map<String, String> headers = new LinkedHashMap<>();
    req.get("headers")
        .fields()
        .forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
    WebhookVerifyOptions options =
        Support.at(f.at("/verify/now").asText())
            .tolerance(java.time.Duration.ofSeconds(f.at("/verify/toleranceSeconds").asLong()));
    JsonNode expect = f.get("expect");
    String outcome = expect.get("outcome").asText();
    String sig = lookup(headers, "x-webhook-signature");
    String ts = lookup(headers, "x-webhook-timestamp");

    if (outcome.equals("error")) {
      String reason = expect.get("reason").asText();
      // Every entry point fails the same way.
      List<Executable> calls = new ArrayList<>();
      calls.add(() -> Webhooks.constructEvent(body, headers, secret, options));
      calls.add(
          () -> Webhooks.constructEvent(req.get("rawBody").asText(), headers, secret, options));
      calls.add(() -> Webhooks.constructEvent(body, sig, ts, secret, options));
      calls.add(() -> Webhooks.verifySignature(body, sig, ts, secret, options));
      for (Executable call : calls) {
        WebhookVerificationException e = assertThrows(WebhookVerificationException.class, call);
        assertEquals(reason, e.reason().value());
        assertEquals(FinlightErrorKind.WEBHOOK_VERIFICATION, e.kind());
        assertFalse(e.getMessage().contains(secret));
      }
      return;
    }

    WebhookPayload bytesResult = Webhooks.constructEvent(body, headers, secret, options);
    WebhookPayload textResult =
        Webhooks.constructEvent(new String(body, StandardCharsets.UTF_8), headers, secret, options);
    WebhookPayload valuesResult = Webhooks.constructEvent(body, sig, ts, secret, options);
    Webhooks.verifySignature(body, sig, ts, secret, options);
    if (outcome.equals("wrongPayloadVersion")) {
      for (WebhookPayload p : List.of(bytesResult, textResult, valuesResult)) {
        WrongPayloadVersion w = assertInstanceOf(WrongPayloadVersion.class, p);
        assertEquals(expect.get("detectedVersion").asInt(), w.detectedVersion());
        assertEquals(expect.get("payloadVersionPresent").asBoolean(), w.payloadVersionPresent());
        assertNull(w.payloadVersion());
        assertEquals(expect.get("link").asText(), w.link());
        assertEquals(Kit.parse(req.get("rawBody").asText()), Kit.parse(w.raw()));
      }
      return;
    }
    JsonNode ev = expect.get("event");
    for (WebhookPayload p : List.of(bytesResult, textResult, valuesResult)) {
      WebhookEvent e = assertInstanceOf(WebhookEvent.class, p);
      assertEquals(ev.get("eventId").asText(), e.eventId());
      assertEquals(ev.get("event").asText(), e.event().value());
      assertEquals(ev.get("occurredAt").asText(), e.occurredAt());
      assertEquals(ev.get("subscriptionId").asText(), e.subscriptionId());
      assertEquals(ev.get("isTest").asBoolean(), e.isTest());
      assertEquals(ev.get("link").asText(), e.data().link());
      assertEquals(3, e.payloadVersion());
      if (ev.has("changedFields")) {
        List<String> changed = new ArrayList<>();
        ev.get("changedFields").forEach(x -> changed.add(x.asText()));
        assertEquals(changed, e.changedFields());
      } else {
        assertNull(e.changedFields(), "absent changedFields stay absent");
      }
      assertEquals(Kit.parse(req.get("rawBody").asText()), Kit.parse(e.rawJson()));
    }
    WebhookEvent e = (WebhookEvent) bytesResult;
    switch (id) {
      case "W-03" ->
          assertFalse(e.data().contentPresent(), "content absent without fullArticleAccess");
      case "W-04" -> {
        Article a = e.data();
        assertFalse(
            a.contentPresent()
                || a.sentimentPresent()
                || a.companiesPresent()
                || a.eventsPresent());
        assertNull(a.availableAt());
        assertEquals(a.firstSeenAt(), e.occurredAt());
      }
      case "W-05" -> assertTrue(e.eventId().startsWith("evt_test"));
      case "W-16" -> {
        JsonNode w01 = Kit.load(Kit.fixtureOf("W-01"));
        assertEquals(w01.at("/expect/event/eventId").asText(), e.eventId());
      }
      default -> {}
    }
  }

  private static String lookup(Map<String, String> headers, String name) {
    return headers.entrySet().stream()
        .filter(h -> h.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }
}
