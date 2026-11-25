package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Access to the vendored contract kit in conformance/. */
final class Kit {
  static final ObjectMapper MAPPER = new ObjectMapper();
  static final String API_KEY =
      "sk_0000000000000000000000000000000000000000000000000000000000000001";

  private Kit() {}

  static Path dir() {
    String p = System.getProperty("finlight.kit");
    Path dir = p != null ? Path.of(p) : Path.of("conformance");
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException("contract kit not found at " + dir.toAbsolutePath());
    }
    return dir;
  }

  static JsonNode load(String relative) {
    try {
      return MAPPER.readTree(dir().resolve(relative).toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String text(String relative) {
    try {
      return Files.readString(dir().resolve(relative));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Case id to case title, in the order of conformance.md. */
  static Map<String, String> cases() {
    Map<String, String> out = new LinkedHashMap<>();
    Matcher m =
        Pattern.compile("(?m)^\\| ([A-Z]-\\d{2}) \\| ([^|]+) \\|").matcher(text("conformance.md"));
    while (m.find()) {
      out.put(m.group(1), m.group(2).trim());
    }
    return out;
  }

  /** The fixture file named in conformance.md for a case. */
  static String fixtureOf(String id) {
    Matcher m =
        Pattern.compile("(?m)^\\| " + id + " \\| [^|]+ \\| `([^`]+)`")
            .matcher(text("conformance.md"));
    if (!m.find()) {
      throw new IllegalArgumentException("no fixture for " + id);
    }
    return m.group(1);
  }

  static List<String> fieldNames(JsonNode node) {
    List<String> out = new ArrayList<>();
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      out.add(it.next());
    }
    return out;
  }

  static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
