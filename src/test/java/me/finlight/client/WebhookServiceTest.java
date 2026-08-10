package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import me.finlight.client.model.Article;
import org.junit.jupiter.api.Test;

class WebhookServiceTest {

  private static final String SECRET = "test-secret";
  private static final String BODY =
      """
      {"link":"https://example.com/news/1","title":"Test Article",\
      "publishDate":"2025-06-01T12:00:00Z","source":"example.com","language":"en",\
      "confidence":"0.95","sentiment":"positive"}""";

  private static String sign(String message, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : digest) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  @Test
  void verifiesValidSignatureWithoutTimestamp() throws Exception {
    Article article = WebhookService.constructEvent(BODY, sign(BODY, SECRET), SECRET);
    assertEquals("Test Article", article.title());
    assertEquals("https://example.com/news/1", article.link());
    assertEquals(0.95, article.confidence());
  }

  @Test
  void verifiesValidSignatureWithPrefix() throws Exception {
    Article article = WebhookService.constructEvent(BODY, "sha256=" + sign(BODY, SECRET), SECRET);
    assertEquals("Test Article", article.title());
  }

  @Test
  void verifiesSignatureWithTimestamp() throws Exception {
    String timestamp = Instant.now().toString();
    String signature = sign(timestamp + "." + BODY, SECRET);
    Article article = WebhookService.constructEvent(BODY, signature, SECRET, timestamp);
    assertEquals("Test Article", article.title());
  }

  @Test
  void rejectsInvalidSignature() {
    assertThrows(
        WebhookVerificationException.class,
        () -> WebhookService.constructEvent(BODY, "deadbeef", SECRET));
  }

  @Test
  void rejectsWrongSecret() throws Exception {
    String signature = sign(BODY, "other-secret");
    assertThrows(
        WebhookVerificationException.class,
        () -> WebhookService.constructEvent(BODY, signature, SECRET));
  }

  @Test
  void rejectsExpiredTimestamp() throws Exception {
    String timestamp = Instant.now().minusSeconds(600).toString();
    String signature = sign(timestamp + "." + BODY, SECRET);
    assertThrows(
        WebhookVerificationException.class,
        () -> WebhookService.constructEvent(BODY, signature, SECRET, timestamp));
  }

  @Test
  void rejectsInvalidJsonPayload() throws Exception {
    String badBody = "not json";
    String signature = sign(badBody, SECRET);
    assertThrows(
        WebhookVerificationException.class,
        () -> WebhookService.constructEvent(badBody, signature, SECRET));
  }
}
