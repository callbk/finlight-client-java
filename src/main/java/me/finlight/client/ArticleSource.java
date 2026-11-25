package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/** The source of an article. {@code domain} is the join key to {@code sources().list()}. */
public final class ArticleSource extends WireObject {
  private final String domain;
  private final @Nullable SourceType type;

  ArticleSource(String domain, @Nullable SourceType type) {
    this.domain = domain;
    this.type = type;
  }

  /** The domain, for example {@code www.reuters.com}. */
  public String domain() {
    return domain;
  }

  /**
   * The source type when the article was indexed, or null when the source is not classified. It can
   * lag {@link Source#type()} after a reclassification.
   */
  public @Nullable SourceType type() {
    return type;
  }

  static ArticleSource read(JsonNode node, String path) throws Wire.Failure {
    ObjectNode o = Wire.object(node, path);
    return new ArticleSource(
        Wire.reqString(o, "domain", path), Wire.optEnum(o, "type", path, SourceType::of));
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    o.put("domain", domain);
    Wire.put(o, "type", type == null ? null : type.value());
    return o;
  }
}
