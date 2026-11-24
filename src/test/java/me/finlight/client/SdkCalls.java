package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds parameter objects from the kit's {@code sdk.args} by calling the builder method that
 * carries the wire key's name (R-NAMES-2: one property per wire key, same name). A key without such
 * a method cannot be sent at all.
 */
final class SdkCalls {
  private SdkCalls() {}

  /** Thrown when the SDK has no way to express a key. */
  static final class NotExpressible extends RuntimeException {
    private static final long serialVersionUID = 1L;

    NotExpressible(String key) {
      super("the SDK has no parameter named " + key);
    }
  }

  static SearchParams search(JsonNode args) {
    SearchParams.Builder b = SearchParams.builder();
    apply(b, args);
    return b.build();
  }

  static StreamParams stream(JsonNode args) {
    StreamParams.Builder b = StreamParams.builder();
    apply(b, args);
    return b.build();
  }

  static RawStreamParams rawStream(JsonNode args) {
    RawStreamParams.Builder b = RawStreamParams.builder();
    apply(b, args);
    return b.build();
  }

  static void apply(Object builder, JsonNode args) {
    for (String key : Kit.fieldNames(args)) {
      JsonNode v = args.get(key);
      try {
        if (v.isArray()) {
          List<String> list = new ArrayList<>();
          v.forEach(x -> list.add(x.asText()));
          invoke(builder, key, List.class, list);
        } else if (v.isTextual()) {
          invoke(builder, key, String.class, v.asText());
        } else if (v.isBoolean()) {
          invoke(builder, key, Boolean.class, v.asBoolean());
        } else if (v.isIntegralNumber() && has(builder, key, Integer.class)) {
          invoke(builder, key, Integer.class, v.asInt());
        } else if (v.isNumber()) {
          invoke(builder, key, Double.class, v.asDouble());
        } else {
          throw new IllegalArgumentException("cannot map " + key + "=" + v);
        }
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof RuntimeException r) {
          throw r;
        }
        throw new IllegalStateException(e);
      }
    }
  }

  private static boolean has(Object builder, String name, Class<?> type) {
    try {
      builder.getClass().getMethod(name, type);
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private static void invoke(Object builder, String name, Class<?> type, Object value)
      throws InvocationTargetException {
    Method m;
    try {
      m = builder.getClass().getMethod(name, type);
    } catch (NoSuchMethodException e) {
      throw new NotExpressible(name);
    }
    try {
      m.invoke(builder, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
