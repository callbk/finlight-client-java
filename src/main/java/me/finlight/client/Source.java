package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A source of the catalog from {@code sources().list()}. */
public final class Source extends WireObject {
  private final String domain;
  private final @Nullable String name;
  private final @Nullable SourceType type;
  private final @Nullable Boolean isDefault;
  private final Gated<Boolean> hasContent;
  private final @Nullable Boolean isCustom;
  private final @Nullable String country;
  private final List<String> languages;
  private final String rawJson;

  private Source(ObjectNode o, String path) throws Wire.Failure {
    this.domain = Wire.reqString(o, "domain", path);
    this.name = Wire.optString(o, "name", path);
    this.type = Wire.optEnum(o, "type", path, SourceType::of);
    this.isDefault = Wire.optBool(o, "isDefault", path);
    this.hasContent =
        Wire.absentOrNull(o, "hasContent")
            ? Gated.absent()
            : Gated.of(Wire.optBool(o, "hasContent", path));
    this.isCustom = Wire.optBool(o, "isCustom", path);
    this.country = Wire.optString(o, "country", path);
    this.languages = Wire.list(o, "languages", path, Wire::string);
    this.rawJson = Json.write(o);
  }

  static Source read(JsonNode node, String path) throws Wire.Failure {
    return new Source(Wire.object(node, path), path);
  }

  /** The domain, the join key to {@link ArticleSource#domain()}. */
  public String domain() {
    return domain;
  }

  /** Display name, or null. */
  public @Nullable String name() {
    return name;
  }

  /** Source type, or null when the source is not classified. */
  public @Nullable SourceType type() {
    return type;
  }

  /** True when the source is in the default pool. */
  public @Nullable Boolean isDefault() {
    return isDefault;
  }

  /**
   * Whether articles of this source carry content. Absent without {@code fullArticleAccess}; absent
   * means unknown, never false.
   */
  public Gated<Boolean> hasContent() {
    return hasContent;
  }

  /** True for a custom source granted to your subscription. */
  public @Nullable Boolean isCustom() {
    return isCustom;
  }

  /** Country of the source, or null. */
  public @Nullable String country() {
    return country;
  }

  /** Languages, the most common first; possibly empty. */
  public List<String> languages() {
    return languages;
  }

  /** The source JSON as received, including keys this SDK version does not know. */
  public String rawJson() {
    return rawJson;
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    o.put("domain", domain);
    Wire.put(o, "name", name);
    Wire.put(o, "type", type == null ? null : type.value());
    Wire.put(o, "isDefault", isDefault);
    if (hasContent.isPresent()) {
      Wire.put(o, "hasContent", hasContent.orNull());
    }
    Wire.put(o, "isCustom", isCustom);
    Wire.put(o, "country", country);
    o.set("languages", Wire.strings(languages));
    return o;
  }
}
