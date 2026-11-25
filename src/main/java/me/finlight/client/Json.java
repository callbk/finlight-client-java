package me.finlight.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** JSON helpers. Jackson stays behind this class and never reaches the public API. */
final class Json {
  // Unknown properties are ignored everywhere (R-MODELS-3). Models are read from trees by hand.
  static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private Json() {}

  static ObjectNode object() {
    return JsonNodeFactory.instance.objectNode();
  }

  /** Parses JSON text; throws on malformed input. */
  static JsonNode parse(String text) throws JsonProcessingException {
    return MAPPER.readTree(text);
  }

  static JsonNode parse(byte[] bytes) throws java.io.IOException {
    return MAPPER.readTree(bytes);
  }

  /** Compact JSON text. */
  static String write(JsonNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  static byte[] bytes(JsonNode node) {
    return write(node).getBytes(StandardCharsets.UTF_8);
  }

  /** Canonical JSON: sorted keys, compact, UTF-8 (R-WS-5 dedupe hash). */
  static String canonical(JsonNode node) {
    StringBuilder b = new StringBuilder();
    canonical(node, b);
    return b.toString();
  }

  private static void canonical(JsonNode node, StringBuilder b) {
    if (node.isObject()) {
      List<String> names = new ArrayList<>();
      for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
        names.add(it.next());
      }
      Collections.sort(names);
      b.append('{');
      for (int i = 0; i < names.size(); i++) {
        if (i > 0) {
          b.append(',');
        }
        b.append(write(JsonNodeFactory.instance.textNode(names.get(i)))).append(':');
        canonical(node.get(names.get(i)), b);
      }
      b.append('}');
    } else if (node.isArray()) {
      b.append('[');
      for (int i = 0; i < node.size(); i++) {
        if (i > 0) {
          b.append(',');
        }
        canonical(node.get(i), b);
      }
      b.append(']');
    } else {
      b.append(write(node));
    }
  }
}
