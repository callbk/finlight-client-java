package me.finlight.client;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Shared shape checks and JSON building of the parameter types (R-REQ-1, R-REQ-3). Only the keys a
 * caller set are written; nothing is defaulted.
 */
final class Params {
  private Params() {}

  static ArrayNode strings(String name, @Nullable Collection<?> values) {
    if (values == null) {
      throw new FinlightValidationException(name, name + " must not be null");
    }
    ArrayNode a = JsonNodeFactory.instance.arrayNode();
    for (Object v : values) {
      if (v == null) {
        throw new FinlightValidationException(name, name + " must not contain null");
      }
      a.add(v.toString());
    }
    return a;
  }

  static ArrayNode strings(String name, @Nullable Object @Nullable [] values) {
    if (values == null) {
      throw new FinlightValidationException(name, name + " must not be null");
    }
    return strings(name, java.util.Arrays.asList(values));
  }

  /** An include list: at most 3 entries; empty means "not sent". */
  static @Nullable ArrayNode include(String name, @Nullable Collection<?> values) {
    ArrayNode a = strings(name, values);
    if (a.size() > 3) {
      throw new FinlightValidationException(name, name + " takes at most 3 values");
    }
    return a.isEmpty() ? null : a;
  }

  static String time(Instant instant) {
    return Times.format(instant);
  }

  static String time(OffsetDateTime time) {
    return Times.format(time.toInstant());
  }

  static String time(ZonedDateTime time) {
    return Times.format(time.toInstant());
  }

  static String date(LocalDate date) {
    return Times.format(date);
  }

  static void set(Map<String, Object> m, String key, @Nullable Object value) {
    if (value == null) {
      m.remove(key);
    } else {
      m.put(key, value);
    }
  }

  static ObjectNode toJson(Map<String, Object> values) {
    ObjectNode o = Json.object();
    values.forEach(
        (k, v) -> {
          if (v instanceof ArrayNode a) {
            o.set(k, a.deepCopy());
          } else if (v instanceof String s) {
            o.put(k, s);
          } else if (v instanceof Boolean b) {
            o.put(k, b);
          } else if (v instanceof Integer i) {
            o.put(k, i);
          } else if (v instanceof Double d) {
            o.put(k, d);
          } else {
            throw new IllegalStateException("unexpected value for " + k);
          }
        });
    return o;
  }
}
