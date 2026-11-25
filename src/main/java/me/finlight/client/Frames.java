package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Stream frame decoder (R-WS-1). A frame with {@code event} is v3. A frame with only {@code action}
 * is a control frame in the v2 form, which the server sends before the first v3 subscribe (admit,
 * pong, preempted, error); it maps to the same types. Any other v2 action, a v2 article frame
 * ({@code sendArticle}) included, is ignored and never delivered as an article.
 */
final class Frames {
  private Frames() {}

  sealed interface Frame
      permits Admitted, Pong, Preempted, ErrorFrame, ArticleFrame, OtherFrame, Ignored {}

  record Admitted(@Nullable String leaseId, @Nullable Long serverNow, @Nullable String clientNonce)
      implements Frame {}

  record Pong() implements Frame {}

  record Preempted(PreemptReason reason, @Nullable String newLeaseId, @Nullable Long timestamp)
      implements Frame {}

  record ErrorFrame(@Nullable String code, String message, List<FieldIssue> details)
      implements Frame {}

  record ArticleFrame(StreamEventKind kind, StreamEventName event, JsonNode data)
      implements Frame {}

  record OtherFrame(StreamEventName event) implements Frame {}

  record Ignored(String reason) implements Frame {}

  static Frame decode(JsonNode frame) {
    if (!frame.isObject()) {
      return new Ignored("not a JSON object");
    }
    JsonNode event = frame.get("event");
    if (event != null && event.isTextual()) {
      return v3(event.textValue(), frame.path("data"));
    }
    JsonNode action = frame.get("action");
    if (action != null && action.isTextual()) {
      return v2(action.textValue(), frame);
    }
    return new Ignored("no event and no action");
  }

  private static Frame v3(String name, JsonNode data) {
    switch (name) {
      case StreamEventNameValues.ARTICLE_CREATED:
      case StreamEventNameValues.ARTICLE_UPDATED:
        if (!data.isObject()) {
          return new Ignored(name + " without an article");
        }
        return new ArticleFrame(
            name.equals(StreamEventNameValues.ARTICLE_CREATED)
                ? StreamEventKind.CREATED
                : StreamEventKind.UPDATED,
            StreamEventName.of(name),
            data);
      case StreamEventNameValues.CONNECTION_ADMITTED:
        return admitted(data);
      case StreamEventNameValues.CONNECTION_PONG:
        return new Pong();
      case StreamEventNameValues.CONNECTION_PREEMPTED:
        return preempted(data);
      case StreamEventNameValues.CONNECTION_ERROR:
        return error(text(data, "code"), message(data.get("message")), data.get("details"));
      default:
        return new OtherFrame(StreamEventName.of(name));
    }
  }

  private static Frame v2(String action, JsonNode frame) {
    switch (action) {
      case "admit":
        return admitted(frame);
      case "pong":
        return new Pong();
      case "preempted":
        return preempted(frame);
      case "error":
        {
          JsonNode text = frame.hasNonNull("error") ? frame.get("error") : frame.get("data");
          return error(null, message(text), null);
        }
      default:
        // sendArticle (a v2 article) and every other v2 action: never delivered.
        return new Ignored("v2 action " + action);
    }
  }

  private static Admitted admitted(JsonNode d) {
    return new Admitted(text(d, "leaseId"), number(d, "serverNow"), text(d, "clientNonce"));
  }

  private static Preempted preempted(JsonNode d) {
    String reason = text(d, "reason");
    return new Preempted(
        PreemptReason.of(reason == null ? "unknown" : reason),
        text(d, "newLeaseId"),
        number(d, "timestamp"));
  }

  private static ErrorFrame error(
      @Nullable String code, String message, @Nullable JsonNode details) {
    List<FieldIssue> issues = new ArrayList<>();
    if (details != null && details.isArray()) {
      for (JsonNode d : details) {
        if (!d.isObject()) {
          continue;
        }
        Map<String, String> constraints = new LinkedHashMap<>();
        JsonNode cs = d.get("constraints");
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
        String issue =
            constraints.isEmpty() ? text(d, "issue") : String.join("; ", constraints.values());
        List<String> allowed = new ArrayList<>();
        JsonNode av = d.get("allowedValues");
        if (av != null && av.isArray()) {
          av.forEach(v -> allowed.add(v.isTextual() ? v.textValue() : v.toString()));
        }
        issues.add(new FieldIssue(text(d, "field"), issue, allowed, constraints));
      }
    }
    return new ErrorFrame(code, message, issues);
  }

  private static String message(@Nullable JsonNode m) {
    if (m == null || m.isNull() || m.isMissingNode()) {
      return "";
    }
    return m.isTextual() ? m.textValue() : Json.write(m);
  }

  private static @Nullable String text(JsonNode o, String key) {
    JsonNode v = o.get(key);
    return v != null && v.isTextual() ? v.textValue() : null;
  }

  private static @Nullable Long number(JsonNode o, String key) {
    JsonNode v = o.get(key);
    return v != null && v.isIntegralNumber() && v.canConvertToLong() ? v.longValue() : null;
  }
}
