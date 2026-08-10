package me.finlight.client.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.finlight.client.FinlightApiException;
import me.finlight.client.FinlightConfig;
import me.finlight.client.FinlightException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs authenticated REST requests with retry and exponential backoff (500ms · 2^(attempt−1)),
 * mirroring the sibling clients: retries on 429 and transient 5xx.
 */
public final class ApiClient {

  private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
  private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);
  private static final Duration BASE_RETRY_DELAY = Duration.ofMillis(500);

  private final FinlightConfig config;
  private final HttpClient http;

  public ApiClient(FinlightConfig config) {
    this.config = config;
    HttpClient custom = config.httpClient();
    this.http =
        custom != null ? custom : HttpClient.newBuilder().connectTimeout(config.timeout()).build();
  }

  /** Sends a GET request and decodes the 2xx response into {@code type}. */
  public <T> T get(String path, Map<String, String> query, TypeReference<T> type) {
    return request("GET", path, query, null, type);
  }

  /** Sends a POST request with a JSON body and decodes the 2xx response into {@code type}. */
  public <T> T post(String path, Object body, TypeReference<T> type) {
    return request("POST", path, Map.of(), body, type);
  }

  private <T> T request(
      String method,
      String path,
      Map<String, String> query,
      @Nullable Object body,
      TypeReference<T> type) {
    HttpRequest request = buildRequest(method, path, query, body);

    for (int attempt = 1; ; attempt++) {
      HttpResponse<String> response = send(request);
      int status = response.statusCode();
      if (status >= 200 && status <= 299) {
        return decode(response.body(), type);
      }
      if (RETRYABLE_STATUS.contains(status) && attempt < config.retryCount()) {
        Duration delay = BASE_RETRY_DELAY.multipliedBy(1L << (attempt - 1));
        log.warn(
            "finlight: retrying request (status={}, attempt={}/{}, delay={}ms)",
            status,
            attempt,
            config.retryCount(),
            delay.toMillis());
        sleep(delay);
        continue;
      }
      throw new FinlightApiException(status, response.body());
    }
  }

  private HttpRequest buildRequest(
      String method, String path, Map<String, String> query, @Nullable Object body) {
    String url = config.baseUrl() + path;
    if (!query.isEmpty()) {
      url +=
          "?"
              + query.entrySet().stream()
                  .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                  .collect(Collectors.joining("&"));
    }

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(config.timeout())
            .header("X-API-KEY", config.apiKey())
            .header("User-Agent", ClientVersion.get());

    if (body != null) {
      builder
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(encodeBody(body)));
    } else {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }
    return builder.build();
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new FinlightException("finlight: request failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new FinlightException("finlight: request interrupted", e);
    }
  }

  private static String encodeBody(Object body) {
    try {
      return Json.mapper().writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new FinlightException("finlight: cannot encode request body", e);
    }
  }

  private static <T> T decode(String body, TypeReference<T> type) {
    try {
      return Json.mapper().readValue(body, type);
    } catch (JsonProcessingException e) {
      throw new FinlightException("finlight: cannot decode response", e);
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new FinlightException("finlight: retry wait interrupted", e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
