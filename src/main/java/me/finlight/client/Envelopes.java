package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the response envelopes. Unknown envelope keys are ignored (R-MODELS-3). */
final class Envelopes {
  private Envelopes() {}

  static JsonNode parse(String body) {
    try {
      JsonNode node = Json.parse(body);
      if (!node.isObject()) {
        throw new FinlightDecodeException("the response is not a JSON object", "", null, null);
      }
      return node;
    } catch (FinlightDecodeException e) {
      throw e;
    } catch (Exception e) {
      throw new FinlightDecodeException("the response is not valid JSON", null, null, e);
    }
  }

  static FinlightDecodeException decodeError(Wire.Failure f, JsonNode raw) {
    return new FinlightDecodeException(
        "could not read the response: " + f.getMessage(), f.path, Json.write(raw), f);
  }

  /** Reads list entries; a broken entry is skipped, logged and recorded (R-MODELS-1). */
  static <T> List<T> entries(
      JsonNode body, String key, Wire.Reader<T> reader, List<SkippedEntry> skipped) {
    JsonNode arr = body.get(key);
    if (arr == null || !arr.isArray()) {
      throw new FinlightDecodeException(
          "the response has no list '" + key + "'", key, Json.write(body), null);
    }
    List<T> out = new ArrayList<>(arr.size());
    for (int i = 0; i < arr.size(); i++) {
      JsonNode item = arr.get(i);
      try {
        out.add(reader.read(item, key + "[" + i + "]"));
      } catch (Wire.Failure f) {
        Log.warn("skipped an entry that could not be read: " + f.getMessage());
        skipped.add(new SkippedEntry(i, Json.write(item), f.getMessage()));
      }
    }
    return out;
  }

  static ArticlePage page(JsonNode body) {
    List<SkippedEntry> skipped = new ArrayList<>();
    List<Article> articles = entries(body, "articles", Article::read, skipped);
    Pagination pagination;
    try {
      pagination = Pagination.read(body.get("pagination"), "pagination");
    } catch (Wire.Failure f) {
      throw decodeError(f, body);
    }
    return new ArticlePage(articles, pagination, skipped);
  }

  static Article article(JsonNode body) {
    try {
      return Article.read(body.get("article"), "article");
    } catch (Wire.Failure f) {
      throw decodeError(f, body);
    }
  }

  static CompanyRecord company(JsonNode body) {
    try {
      return CompanyRecord.read(body.get("company"), "company");
    } catch (Wire.Failure f) {
      throw decodeError(f, body);
    }
  }

  static CompanyList companies(JsonNode body) {
    List<SkippedEntry> skipped = new ArrayList<>();
    return new CompanyList(entries(body, "companies", CompanyRecord::read, skipped), skipped);
  }

  static SourceList sources(JsonNode body) {
    List<SkippedEntry> skipped = new ArrayList<>();
    return new SourceList(entries(body, "sources", Source::read, skipped), skipped);
  }
}
