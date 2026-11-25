package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R-HOOK-1 to R-HOOK-3 beyond the kit cases. */
class RulesWebhookTest {
  private static final String TS = "2026-11-23T21:04:05.123Z";

  private static JsonNode created() {
    return Kit.load("fixtures/forward-compat/envelope-keys.json").at("/envelopes/2/wire");
  }

  private static WebhookPayload construct(JsonNode body) {
    return Support.signedEvent(body);
  }

  @Test
  void payloadVersionMustBeTheNumber3() {
    ObjectNode s = created().deepCopy();
    s.put("payloadVersion", "3");
    WrongPayloadVersion w = assertInstanceOf(WrongPayloadVersion.class, construct(s));
    assertEquals("\"3\"", w.payloadVersion(), "the raw value as received");
    assertTrue(w.payloadVersionPresent());
    assertEquals(2, w.detectedVersion());
    assertEquals(s.at("/data/link").asText(), w.link());

    ObjectNode b = created().deepCopy();
    b.put("payloadVersion", true);
    assertInstanceOf(WrongPayloadVersion.class, construct(b));

    ObjectNode f = created().deepCopy();
    f.put("payloadVersion", 3.0);
    assertInstanceOf(WebhookEvent.class, construct(f), "3.0 counts as 3");
  }

  @Test
  void aTamperedV2BodyFailsTheSignatureFirst() {
    JsonNode v2 = Kit.load("fixtures/webhook/v2-body.json");
    byte[] body =
        v2.at("/request/rawBody")
            .asText()
            .replace("Example", "Exampel")
            .getBytes(StandardCharsets.UTF_8);
    WebhookVerificationException e =
        assertThrows(
            WebhookVerificationException.class,
            () ->
                Webhooks.constructEvent(
                    body,
                    v2.at("/request/headers/X-Webhook-Signature").asText(),
                    TS,
                    v2.get("secret").asText(),
                    Support.at(TS)));
    assertEquals(WebhookVerificationReason.SIGNATURE_MISMATCH, e.reason());
  }

  @Test
  void headerCollections() {
    byte[] body = created().toString().getBytes(StandardCharsets.UTF_8);
    String sig = Support.sign(Support.SECRET, TS, body);
    WebhookVerifyOptions at = Support.at(TS);
    // Multi-valued maps, CGI names, any casing.
    assertInstanceOf(
        WebhookEvent.class,
        Webhooks.constructEvent(
            body,
            Map.of("X-WEBHOOK-SIGNATURE", List.of(sig), "x-webhook-timestamp", List.of(TS)),
            Support.SECRET,
            at));
    assertInstanceOf(
        WebhookEvent.class,
        Webhooks.constructEvent(
            body,
            Map.of("HTTP_X_WEBHOOK_SIGNATURE", sig, "HTTP_X_WEBHOOK_TIMESTAMP", TS),
            Support.SECRET,
            at));
    assertInstanceOf(
        WebhookEvent.class,
        Webhooks.constructEvent(
            body,
            Map.of("x-webhook-signature", new String[] {sig}, "X-Webhook-Timestamp", TS),
            Support.SECRET,
            at));
    // A header twice, as two values or under two spellings.
    assertReason(
        WebhookVerificationReason.DUPLICATE_HEADER,
        () ->
            Webhooks.constructEvent(
                body,
                Map.of("x-webhook-signature", List.of(sig, sig), "x-webhook-timestamp", TS),
                Support.SECRET,
                at));
    assertReason(
        WebhookVerificationReason.DUPLICATE_HEADER,
        () ->
            Webhooks.constructEvent(
                body,
                Map.of(
                    "x-webhook-signature",
                    sig,
                    "X-Webhook-Signature",
                    sig,
                    "x-webhook-timestamp",
                    TS),
                Support.SECRET,
                at));
    assertReason(
        WebhookVerificationReason.NON_ASCII_HEADER,
        () -> Webhooks.constructEvent(body, sig, "2026-11-23T21:04:05.123Zé", Support.SECRET, at));
    assertReason(
        WebhookVerificationReason.INVALID_SECRET,
        () -> Webhooks.constructEvent(body, sig, TS, "", at));
    assertReason(
        WebhookVerificationReason.INVALID_RAW_BODY,
        () -> Webhooks.constructEvent((byte[]) null, sig, TS, Support.SECRET, at));
    assertReason(
        WebhookVerificationReason.INVALID_RAW_BODY,
        () -> Webhooks.constructEvent((String) null, sig, TS, Support.SECRET, at));
  }

  @Test
  void aSignedBodyThatIsNotAnObjectIsADecodeError() {
    for (String text : new String[] {"[1,2]", "not json"}) {
      byte[] body = text.getBytes(StandardCharsets.UTF_8);
      assertThrows(
          FinlightDecodeException.class,
          () ->
              Webhooks.constructEvent(
                  body,
                  Support.sign(Support.SECRET, TS, body),
                  TS,
                  Support.SECRET,
                  Support.at(TS)));
    }
  }

  @Test
  void nonAsciiBodiesVerifyAsBytesAndAsText() {
    JsonNode f = Kit.load("fixtures/webhook/created-enriched.json");
    String text = f.at("/request/rawBody").asText();
    assertTrue(text.chars().anyMatch(c -> c > 127), "the vector has non-ASCII text");
    Webhooks.verifySignature(
        text,
        f.at("/request/headers/X-Webhook-Signature").asText(),
        TS,
        f.get("secret").asText(),
        Support.at(TS));
  }

  private static void assertReason(
      WebhookVerificationReason reason, org.junit.jupiter.api.function.Executable e) {
    assertEquals(reason, assertThrows(WebhookVerificationException.class, e).reason());
  }
}
