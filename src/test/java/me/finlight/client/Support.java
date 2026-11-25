package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.function.Executable;

/** Small helpers shared by the tests. */
final class Support {
  static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private Support() {}

  static FinlightClient client(HttpMock mock, FakeClock clock) {
    return client(mock, clock, b -> {});
  }

  static FinlightClient client(
      HttpMock mock, FakeClock clock, Consumer<FinlightConfig.Builder> more) {
    FinlightConfig.Builder b = FinlightConfig.builder(Kit.API_KEY).baseUrl(mock.url()).clock(clock);
    more.accept(b);
    return new FinlightClient(b.build());
  }

  static String sign(String secret, String timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static WebhookVerifyOptions at(String now) {
    return WebhookVerifyOptions.defaults().now(Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
  }

  /** Signs a JSON body and runs constructEvent on it. */
  static WebhookPayload signedEvent(JsonNode body) {
    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
    String ts = "2026-11-23T21:04:05.123Z";
    return Webhooks.constructEvent(bytes, sign(SECRET, ts, bytes), ts, SECRET, at(ts));
  }

  static Executable named(Executable e) {
    return e;
  }
}
