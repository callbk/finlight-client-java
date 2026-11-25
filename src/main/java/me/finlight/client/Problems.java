package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads error bodies (R-ERR-1) in this order: {@code application/problem+json}, the NestJS form
 * {@code {statusCode, message}}, then a body that is not JSON. Every 429 is classified here and
 * nowhere else.
 */
final class Problems {
  static final String MONTHLY_TEXT = "Exceeded token limit";
  static final String BURST_TEXT = "Exceeded rate limit";

  private Problems() {}

  static ApiError parse(
      int status,
      @Nullable String contentType,
      @Nullable String body,
      @Nullable String sentRequestId,
      @Nullable String retryAfterHeader) {
    ApiError e = new ApiError();
    e.status = status;
    e.raw = body == null || body.isEmpty() ? null : body;
    JsonNode json = null;
    if (e.raw != null) {
      try {
        json = Json.parse(e.raw);
      } catch (Exception ignored) {
        json = null;
      }
    }
    boolean problemType =
        contentType != null && contentType.toLowerCase(Locale.ROOT).contains("problem+json");
    if (json != null && json.isObject()) {
      if (problemType || json.hasNonNull("code") || json.has("type") || json.has("errors")) {
        readProblem(e, json);
        e.apiKind = e.code == null ? statusKind(status) : ApiError.kindOf(e.code);
      } else if (json.has("statusCode") || json.has("message") || json.has("error")) {
        readNest(e, json);
        e.apiKind = statusKind(status);
      } else {
        e.apiKind = ApiErrorKind.OTHER;
      }
    } else {
      e.apiKind = ApiErrorKind.OTHER;
      e.title = "HTTP " + status;
    }
    if (e.requestId == null) {
      e.requestId = sentRequestId;
    }
    if (e.apiKind == ApiErrorKind.RATE_LIMITED) {
      classifyRateLimit(e, json, retryAfterHeader);
    }
    return e;
  }

  private static void readProblem(ApiError e, JsonNode json) {
    String code = text(json, "code");
    e.code = code == null ? null : ProblemCode.of(code);
    e.type = text(json, "type");
    e.title = text(json, "title");
    e.detail = text(json, "detail");
    e.requestId = text(json, "requestId");
    JsonNode errors = json.get("errors");
    if (errors != null && errors.isArray()) {
      for (JsonNode item : errors) {
        if (item.isObject()) {
          e.errors.add(issue(item));
        }
      }
    }
  }

  static FieldIssue issue(JsonNode item) {
    List<String> allowed = new ArrayList<>();
    JsonNode av = item.get("allowedValues");
    if (av != null && av.isArray()) {
      av.forEach(v -> allowed.add(v.isTextual() ? v.textValue() : v.toString()));
    }
    Map<String, String> constraints = new LinkedHashMap<>();
    JsonNode cs = item.get("constraints");
    if (cs != null && cs.isObject()) {
      cs.fields()
          .forEachRemaining(
              f ->
                  constraints.put(
                      f.getKey(),
                      f.getValue().isTextual()
                          ? f.getValue().textValue()
                          : f.getValue().toString()));
    }
    return new FieldIssue(text(item, "field"), text(item, "issue"), allowed, constraints);
  }

  private static void readNest(ApiError e, JsonNode json) {
    JsonNode message = json.get("message");
    if (message != null && message.isTextual()) {
      e.detail = message.textValue();
    } else if (message != null && message.isArray()) {
      List<String> parts = new ArrayList<>();
      for (JsonNode m : message) {
        String s = m.isTextual() ? m.textValue() : m.toString();
        parts.add(s);
        e.errors.add(new FieldIssue(null, s, List.of(), Map.of()));
      }
      e.detail = String.join("; ", parts);
    }
    e.title = text(json, "error");
    e.requestId = text(json, "requestId");
  }

  /** The kind when there is no problem code: the status decides. */
  static ApiErrorKind statusKind(int status) {
    switch (status) {
      case 400:
        return ApiErrorKind.VALIDATION_FAILED;
      case 401:
        return ApiErrorKind.UNAUTHORIZED;
      case 403:
        return ApiErrorKind.FORBIDDEN;
      case 404:
        return ApiErrorKind.NOT_FOUND;
      case 429:
        return ApiErrorKind.RATE_LIMITED;
      default:
        return ApiErrorKind.OTHER;
    }
  }

  private static void classifyRateLimit(
      ApiError e, @Nullable JsonNode json, @Nullable String retryAfterHeader) {
    String limitType = json == null ? null : text(json, "limitType");
    if (limitType != null) {
      e.limitType = RateLimitType.of(limitType);
    } else if (e.detail != null && e.detail.contains(MONTHLY_TEXT)) {
      e.limitType = RateLimitType.MONTHLY;
    } else if (e.detail != null && e.detail.contains(BURST_TEXT)) {
      e.limitType = RateLimitType.BURST;
    } else {
      e.limitType = RateLimitType.UNKNOWN;
    }
    e.retryAfter = retryAfter(retryAfterHeader);
  }

  static @Nullable Duration retryAfter(@Nullable String header) {
    if (header == null) {
      return null;
    }
    String h = header.trim();
    if (h.isEmpty() || h.length() > 9) {
      return null;
    }
    for (int i = 0; i < h.length(); i++) {
      if (h.charAt(i) < '0' || h.charAt(i) > '9') {
        return null;
      }
    }
    return Duration.ofSeconds(Long.parseLong(h));
  }

  static @Nullable String text(JsonNode json, String key) {
    JsonNode v = json.get(key);
    return v != null && v.isTextual() ? v.textValue() : null;
  }
}
