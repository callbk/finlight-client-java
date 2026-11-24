package me.finlight.examples;

import me.finlight.client.FinlightClient;
import me.finlight.client.FinlightConfig;

/** Builds a client from FINLIGHT_API_KEY and the optional FINLIGHT_BASE_URL, FINLIGHT_WSS_URL. */
final class Env {
  private Env() {}

  static FinlightClient client() {
    String key = System.getenv("FINLIGHT_API_KEY");
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("set FINLIGHT_API_KEY");
    }
    FinlightConfig.Builder config = FinlightConfig.builder(key);
    String baseUrl = System.getenv("FINLIGHT_BASE_URL");
    if (baseUrl != null && !baseUrl.isBlank()) {
      config.baseUrl(baseUrl);
    }
    String wssUrl = System.getenv("FINLIGHT_WSS_URL");
    if (wssUrl != null && !wssUrl.isBlank()) {
      config.wssUrl(wssUrl);
    }
    return new FinlightClient(config.build());
  }
}
