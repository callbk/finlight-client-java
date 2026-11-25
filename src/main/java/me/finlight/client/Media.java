package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/** An image of an article. */
public final class Media extends WireObject {
  private final @Nullable String url;

  Media(@Nullable String url) {
    this.url = url;
  }

  /** The image URL. */
  public @Nullable String url() {
    return url;
  }

  static Media read(JsonNode node, String path) throws Wire.Failure {
    ObjectNode o = Wire.object(node, path);
    return new Media(Wire.optString(o, "url", path));
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    Wire.put(o, "url", url);
    return o;
  }
}
