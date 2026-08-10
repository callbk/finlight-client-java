package me.finlight.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import me.finlight.client.internal.FlexTimestamps;
import me.finlight.client.internal.Json;
import me.finlight.client.model.Article;
import org.jspecify.annotations.Nullable;

/**
 * Securely receives and verifies webhook events from finlight.
 *
 * <p>Webhooks provide real-time notifications when new articles are published. This service
 * verifies the HMAC-SHA256 signature and protects against replay attacks.
 *
 * <pre>{@code
 * // e.g. in a Spring controller — body must be the unparsed request body
 * Article article = WebhookService.constructEvent(
 *     rawBody,
 *     request.getHeader("X-Webhook-Signature"),
 *     endpointSecret,
 *     request.getHeader("X-Webhook-Timestamp"));
 * }</pre>
 */
public final class WebhookService {

  private static final String SIGNATURE_PREFIX = "sha256=";
  private static final Duration REPLAY_TOLERANCE = Duration.ofMinutes(5);
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private WebhookService() {}

  /**
   * Verifies a finlight webhook and returns the contained article.
   *
   * @param rawBody the unmodified request body
   * @param signature the {@code X-Webhook-Signature} header value (with or without the {@code
   *     sha256=} prefix)
   * @param endpointSecret your webhook secret from the finlight dashboard
   * @param timestamp the {@code X-Webhook-Timestamp} header value, or null if the webhook has none;
   *     when present it is included in the signed message and checked against a 5-minute replay
   *     tolerance
   * @throws WebhookVerificationException if signature, timestamp, or payload validation fails
   */
  public static Article constructEvent(
      String rawBody, String signature, String endpointSecret, @Nullable String timestamp) {
    String normalized =
        signature.startsWith(SIGNATURE_PREFIX)
            ? signature.substring(SIGNATURE_PREFIX.length())
            : signature;

    String message = timestamp != null ? timestamp + "." + rawBody : rawBody;
    String expected = hmacSha256Hex(message, endpointSecret);

    if (!secureEquals(normalized, expected)) {
      throw new WebhookVerificationException("Invalid webhook signature");
    }

    if (timestamp != null) {
      verifyTimestamp(timestamp);
    }

    try {
      return Json.mapper().readValue(rawBody, Article.class);
    } catch (JsonProcessingException e) {
      throw new WebhookVerificationException("Invalid JSON payload");
    }
  }

  /** Verifies a webhook without a timestamp header. */
  public static Article constructEvent(String rawBody, String signature, String endpointSecret) {
    return constructEvent(rawBody, signature, endpointSecret, null);
  }

  private static void verifyTimestamp(String timestamp) {
    Instant webhookTime;
    try {
      webhookTime = FlexTimestamps.parse(timestamp);
    } catch (DateTimeParseException e) {
      throw new WebhookVerificationException("Invalid timestamp format");
    }
    Duration difference = Duration.between(webhookTime, Instant.now()).abs();
    if (difference.compareTo(REPLAY_TOLERANCE) > 0) {
      throw new WebhookVerificationException("Webhook timestamp outside allowed tolerance");
    }
  }

  private static String hmacSha256Hex(String message, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      char[] hex = new char[digest.length * 2];
      for (int i = 0; i < digest.length; i++) {
        hex[i * 2] = HEX[(digest[i] >> 4) & 0xf];
        hex[i * 2 + 1] = HEX[digest[i] & 0xf];
      }
      return new String(hex);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new FinlightException("finlight: cannot compute webhook signature", e);
    }
  }

  private static boolean secureEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
