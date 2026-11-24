package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Typed reads from JSON trees (R-MODELS-1). Values are never coerced: a string where a number
 * belongs fails the entry, as does a missing or null required key. Optional keys read as null when
 * missing or null.
 */
final class Wire {
  private Wire() {}

  /** A value that does not fit the model. Becomes a skipped entry or a decode error. */
  static final class Failure extends Exception {
    private static final long serialVersionUID = 1L;
    final String path;

    Failure(String path, String message) {
      super(path + ": " + message, null, false, false);
      this.path = path;
    }
  }

  /** Reads one element. */
  interface Reader<T> {
    T read(JsonNode node, String path) throws Failure;
  }

  static String at(String path, String key) {
    return path.isEmpty() ? key : path + "." + key;
  }

  static ObjectNode object(@Nullable JsonNode node, String path) throws Failure {
    if (node == null || !node.isObject()) {
      throw new Failure(path, "expected an object, got " + typeName(node));
    }
    return (ObjectNode) node;
  }

  static String typeName(@Nullable JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return "nothing";
    }
    return node.getNodeType().name().toLowerCase(java.util.Locale.ROOT);
  }

  static boolean absentOrNull(JsonNode obj, String key) {
    JsonNode v = obj.get(key);
    return v == null || v.isNull();
  }

  static String reqString(JsonNode obj, String key, String path) throws Failure {
    JsonNode v = obj.get(key);
    if (v == null || v.isNull()) {
      throw new Failure(at(path, key), "required key is missing or null");
    }
    if (!v.isTextual()) {
      throw new Failure(at(path, key), "expected a string, got " + typeName(v));
    }
    return v.textValue();
  }

  static @Nullable String optString(JsonNode obj, String key, String path) throws Failure {
    JsonNode v = obj.get(key);
    if (v == null || v.isNull()) {
      return null;
    }
    if (!v.isTextual()) {
      throw new Failure(at(path, key), "expected a string, got " + typeName(v));
    }
    return v.textValue();
  }

  static long integer(@Nullable JsonNode v, String path) throws Failure {
    if (v == null || v.isNull()) {
      throw new Failure(path, "required key is missing or null");
    }
    if (!v.isIntegralNumber() || !v.canConvertToLong()) {
      throw new Failure(path, "expected an integer, got " + typeName(v));
    }
    return v.longValue();
  }

  static long reqLong(JsonNode obj, String key, String path) throws Failure {
    return integer(obj.get(key), at(path, key));
  }

  static @Nullable Long optLong(JsonNode obj, String key, String path) throws Failure {
    return absentOrNull(obj, key) ? null : integer(obj.get(key), at(path, key));
  }

  static @Nullable Integer optInt(JsonNode obj, String key, String path) throws Failure {
    Long v = optLong(obj, key, path);
    if (v == null) {
      return null;
    }
    if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
      throw new Failure(at(path, key), "integer out of range");
    }
    return v.intValue();
  }

  static @Nullable Double optDouble(JsonNode obj, String key, String path) throws Failure {
    JsonNode v = obj.get(key);
    if (v == null || v.isNull()) {
      return null;
    }
    if (!v.isNumber()) {
      throw new Failure(at(path, key), "expected a number, got " + typeName(v));
    }
    return v.doubleValue();
  }

  static @Nullable Boolean optBool(JsonNode obj, String key, String path) throws Failure {
    JsonNode v = obj.get(key);
    if (v == null || v.isNull()) {
      return null;
    }
    if (!v.isBoolean()) {
      throw new Failure(at(path, key), "expected a boolean, got " + typeName(v));
    }
    return v.booleanValue();
  }

  /** A list that reads as empty when missing or null. */
  static <T> List<T> list(JsonNode obj, String key, String path, Reader<T> reader) throws Failure {
    JsonNode v = obj.get(key);
    if (v == null || v.isNull()) {
      return List.of();
    }
    return elements(v, at(path, key), reader);
  }

  /** A gated list (R-NULL-1): absent stays absent; null is not allowed and fails the entry. */
  static <T> Gated<List<T>> gatedList(JsonNode obj, String key, String path, Reader<T> reader)
      throws Failure {
    JsonNode v = obj.get(key);
    if (v == null) {
      return Gated.absent();
    }
    if (v.isNull()) {
      // The contract never sends null for these keys; the language null means "not sent".
      return Gated.absent();
    }
    return Gated.of(elements(v, at(path, key), reader));
  }

  static <T> List<T> elements(JsonNode v, String path, Reader<T> reader) throws Failure {
    if (!v.isArray()) {
      throw new Failure(path, "expected an array, got " + typeName(v));
    }
    List<T> out = new ArrayList<>(v.size());
    for (int i = 0; i < v.size(); i++) {
      out.add(reader.read(v.get(i), path + "[" + i + "]"));
    }
    return Collections.unmodifiableList(out);
  }

  static String string(JsonNode v, String path) throws Failure {
    if (!v.isTextual()) {
      throw new Failure(path, "expected a string, got " + typeName(v));
    }
    return v.textValue();
  }

  static <T> @Nullable T optEnum(JsonNode obj, String key, String path, Function<String, T> of)
      throws Failure {
    String s = optString(obj, key, path);
    return s == null ? null : of.apply(s);
  }

  // --- writing (toWire) ------------------------------------------------------------------------

  static final JsonNodeFactory F = JsonNodeFactory.instance;

  static void put(ObjectNode o, String key, @Nullable String v) {
    if (v == null) {
      o.putNull(key);
    } else {
      o.put(key, v);
    }
  }

  static void put(ObjectNode o, String key, @Nullable Double v) {
    if (v == null) {
      o.putNull(key);
    } else {
      o.put(key, v);
    }
  }

  static void put(ObjectNode o, String key, @Nullable Long v) {
    if (v == null) {
      o.putNull(key);
    } else {
      o.put(key, v);
    }
  }

  static void put(ObjectNode o, String key, @Nullable Boolean v) {
    if (v == null) {
      o.putNull(key);
    } else {
      o.put(key, v);
    }
  }

  static ArrayNode strings(List<String> values) {
    ArrayNode a = F.arrayNode();
    values.forEach(a::add);
    return a;
  }
}
