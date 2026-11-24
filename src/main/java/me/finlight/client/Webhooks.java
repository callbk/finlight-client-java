package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Verifies and reads webhooks on your server (R-HOOK-1 to R-HOOK-3).
 *
 * <p>Pass the <b>raw</b> request body, exactly as received, before any framework parses it. The
 * HMAC-SHA256 runs over {@code X-Webhook-Timestamp + "." + rawBody}, keyed with the UTF-8 bytes of
 * the secret. The timestamp is required and must be within the tolerance (300 s by default) in both
 * directions.
 */
public final class Webhooks {
  /** Default tolerance of the timestamp: 300 s. */
  public static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);

  private static final String SIGNATURE = "x-webhook-signature";
  private static final String TIMESTAMP = "x-webhook-timestamp";
  private static final String PREFIX = "sha256=";

  private Webhooks() {}

  /**
   * Verifies a delivery and reads it.
   *
   * @param rawBody the body bytes exactly as received
   * @param headers the request headers; names are matched without regard to case (also the CGI form
   *     {@code HTTP_X_WEBHOOK_TIMESTAMP}); values can be strings, string collections or arrays
   * @param secret the webhook secret (the 64-character string, not hex-decoded)
   * @return a {@link WebhookEvent}, or {@link WrongPayloadVersion} for an old-format body
   * @throws WebhookVerificationException when verification fails (answer 400, never 410)
   * @throws FinlightDecodeException when a correctly signed body cannot be read
   */
  public static WebhookPayload constructEvent(
      byte[] rawBody, Map<String, ?> headers, String secret) {
    return constructEvent(rawBody, headers, secret, WebhookVerifyOptions.defaults());
  }

  /** {@link #constructEvent(byte[], Map, String)} with options (tolerance, clock). */
  public static WebhookPayload constructEvent(
      byte[] rawBody, Map<String, ?> headers, String secret, WebhookVerifyOptions options) {
    if (headers == null) {
      throw new WebhookVerificationException(
          WebhookVerificationReason.MISSING_SIGNATURE, "no headers were given");
    }
    String signature = header(headers, SIGNATURE);
    String timestamp = header(headers, TIMESTAMP);
    return constructEvent(rawBody, signature, timestamp, secret, options);
  }

  /** {@link #constructEvent(byte[], Map, String)} with the body as its UTF-8 text. */
  public static WebhookPayload constructEvent(
      String rawBody, Map<String, ?> headers, String secret) {
    return constructEvent(utf8(rawBody), headers, secret, WebhookVerifyOptions.defaults());
  }

  /** {@link #constructEvent(String, Map, String)} with options. */
  public static WebhookPayload constructEvent(
      String rawBody, Map<String, ?> headers, String secret, WebhookVerifyOptions options) {
    return constructEvent(utf8(rawBody), headers, secret, options);
  }

  /** Verifies and reads a delivery from the two header values. */
  public static WebhookPayload constructEvent(
      byte[] rawBody, @Nullable String signature, @Nullable String timestamp, String secret) {
    return constructEvent(rawBody, signature, timestamp, secret, WebhookVerifyOptions.defaults());
  }

  /** Verifies and reads a delivery from the two header values, with options. */
  public static WebhookPayload constructEvent(
      byte[] rawBody,
      @Nullable String signature,
      @Nullable String timestamp,
      String secret,
      WebhookVerifyOptions options) {
    verifySignature(rawBody, signature, timestamp, secret, options);
    return read(rawBody);
  }

  /** Verifies and reads a delivery from the two header values; the body as UTF-8 text. */
  public static WebhookPayload constructEvent(
      String rawBody, @Nullable String signature, @Nullable String timestamp, String secret) {
    return constructEvent(utf8(rawBody), signature, timestamp, secret);
  }

  /** As above, with options. */
  public static WebhookPayload constructEvent(
      String rawBody,
      @Nullable String signature,
      @Nullable String timestamp,
      String secret,
      WebhookVerifyOptions options) {
    return constructEvent(utf8(rawBody), signature, timestamp, secret, options);
  }

  /**
   * Only verifies the signature and the timestamp.
   *
   * @throws WebhookVerificationException when verification fails
   */
  public static void verifySignature(
      byte[] rawBody, @Nullable String signature, @Nullable String timestamp, String secret) {
    verifySignature(rawBody, signature, timestamp, secret, WebhookVerifyOptions.defaults());
  }

  /** Only verifies, with the body as UTF-8 text. */
  public static void verifySignature(
      String rawBody, @Nullable String signature, @Nullable String timestamp, String secret) {
    verifySignature(utf8(rawBody), signature, timestamp, secret, WebhookVerifyOptions.defaults());
  }

  /** Only verifies, with options. */
  public static void verifySignature(
      String rawBody,
      @Nullable String signature,
      @Nullable String timestamp,
      String secret,
      WebhookVerifyOptions options) {
    verifySignature(utf8(rawBody), signature, timestamp, secret, options);
  }

  /** Only verifies, with options. */
  public static void verifySignature(
      byte[] rawBody,
      @Nullable String signature,
      @Nullable String timestamp,
      String secret,
      WebhookVerifyOptions options) {
    if (rawBody == null) {
      throw fail(
          WebhookVerificationReason.INVALID_RAW_BODY,
          "pass the raw request body (bytes or their UTF-8 text), not parsed JSON");
    }
    if (secret == null || secret.isEmpty()) {
      throw fail(WebhookVerificationReason.INVALID_SECRET, "the webhook secret is empty");
    }
    if (options == null) {
      throw fail(WebhookVerificationReason.INVALID_SECRET, "options must not be null");
    }
    if (signature == null || signature.isEmpty()) {
      throw fail(WebhookVerificationReason.MISSING_SIGNATURE, "no X-Webhook-Signature header");
    }
    if (timestamp == null || timestamp.isEmpty()) {
      throw fail(WebhookVerificationReason.MISSING_TIMESTAMP, "no X-Webhook-Timestamp header");
    }
    if (!ascii(signature) || !ascii(timestamp)) {
      throw fail(WebhookVerificationReason.NON_ASCII_HEADER, "a webhook header is not ASCII");
    }
    if (!signature.startsWith(PREFIX)) {
      throw fail(
          WebhookVerificationReason.UNSUPPORTED_SIGNATURE_SCHEME,
          "the signature must start with sha256=");
    }
    Instant sent = Times.parse(timestamp);
    if (sent == null) {
      throw fail(
          WebhookVerificationReason.UNPARSEABLE_TIMESTAMP,
          "X-Webhook-Timestamp is not an RFC 3339 time with a zone");
    }
    byte[] expected = hex(hmac(secret, timestamp, rawBody)).getBytes(StandardCharsets.US_ASCII);
    byte[] got =
        signature
            .substring(PREFIX.length())
            .toLowerCase(Locale.ROOT)
            .getBytes(StandardCharsets.US_ASCII);
    if (got.length != expected.length || !MessageDigest.isEqual(got, expected)) {
      throw fail(WebhookVerificationReason.SIGNATURE_MISMATCH, "the signature does not match");
    }
    long skew = Math.abs(options.now().millis() - sent.toEpochMilli());
    if (skew > options.tolerance().toMillis()) {
      throw fail(
          WebhookVerificationReason.TIMESTAMP_OUTSIDE_TOLERANCE,
          "the timestamp is outside the tolerance of " + options.tolerance().toSeconds() + " s");
    }
  }

  private static WebhookPayload read(byte[] rawBody) {
    JsonNode node;
    try {
      node = Json.parse(rawBody);
    } catch (Exception e) {
      throw new FinlightDecodeException("the webhook body is not valid JSON", null, null, e);
    }
    if (node == null || !node.isObject()) {
      throw new FinlightDecodeException("the webhook body is not a JSON object", "", null, null);
    }
    JsonNode pv = node.get("payloadVersion");
    boolean v3 = pv != null && pv.isNumber() && pv.doubleValue() == 3.0;
    if (!v3) {
      return new WrongPayloadVersion(
          pv == null ? null : Json.write(pv), Json.write(node), link(node));
    }
    try {
      return WebhookEvent.read(node);
    } catch (Wire.Failure f) {
      throw new FinlightDecodeException(
          "could not read the webhook event: " + f.getMessage(), f.path, null, f);
    }
  }

  private static @Nullable String link(JsonNode node) {
    JsonNode l = node.get("link");
    if (l != null && l.isTextual()) {
      return l.textValue();
    }
    JsonNode data = node.get("data");
    if (data != null && data.isObject()) {
      JsonNode dl = data.get("link");
      if (dl != null && dl.isTextual()) {
        return dl.textValue();
      }
    }
    JsonNode article = node.get("article");
    if (article != null && article.isObject() && article.path("link").isTextual()) {
      return article.get("link").textValue();
    }
    return null;
  }

  /** Finds one header case-insensitively; also the CGI form HTTP_X_...; fails on duplicates. */
  private static @Nullable String header(Map<String, ?> headers, String name) {
    List<String> values = new ArrayList<>();
    for (Map.Entry<String, ?> e : headers.entrySet()) {
      if (e.getKey() == null || !normalize(e.getKey()).equals(name)) {
        continue;
      }
      Object v = e.getValue();
      if (v == null) {
        continue;
      }
      if (v instanceof Collection<?> c) {
        for (Object x : c) {
          if (x != null) {
            values.add(x.toString());
          }
        }
      } else if (v instanceof Object[] arr) {
        for (Object x : arr) {
          if (x != null) {
            values.add(x.toString());
          }
        }
      } else {
        values.add(v.toString());
      }
    }
    if (values.size() > 1) {
      throw fail(
          WebhookVerificationReason.DUPLICATE_HEADER,
          "the header " + name + " was sent more than once");
    }
    return values.isEmpty() ? null : values.get(0);
  }

  private static String normalize(String key) {
    String k = key.trim().toLowerCase(Locale.ROOT);
    if (k.startsWith("http_")) {
      k = k.substring(5).replace('_', '-');
    }
    return k;
  }

  private static boolean ascii(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > 0x7e || s.charAt(i) < 0x20) {
        return false;
      }
    }
    return true;
  }

  private static byte[] hmac(String secret, String timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
      mac.update((byte) '.');
      return mac.doFinal(body);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 is not available", e);
    }
  }

  private static String hex(byte[] bytes) {
    char[] digits = "0123456789abcdef".toCharArray();
    StringBuilder b = new StringBuilder(bytes.length * 2);
    for (byte x : bytes) {
      b.append(digits[(x >> 4) & 15]).append(digits[x & 15]);
    }
    return b.toString();
  }

  private static byte[] utf8(String body) {
    if (body == null) {
      throw fail(
          WebhookVerificationReason.INVALID_RAW_BODY,
          "pass the raw request body (bytes or their UTF-8 text), not parsed JSON");
    }
    return body.getBytes(StandardCharsets.UTF_8);
  }

  private static WebhookVerificationException fail(
      WebhookVerificationReason reason, String message) {
    return new WebhookVerificationException(reason, "webhook verification failed: " + message);
  }
}
