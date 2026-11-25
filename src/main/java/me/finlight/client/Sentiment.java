package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/** Sentiment of an article. */
public final class Sentiment extends WireObject {
  private final @Nullable SentimentLabel label;
  private final @Nullable Double confidence;

  Sentiment(@Nullable SentimentLabel label, @Nullable Double confidence) {
    this.label = label;
    this.confidence = confidence;
  }

  /** {@code positive}, {@code neutral}, {@code negative}, a newer value, or null. */
  public @Nullable SentimentLabel label() {
    return label;
  }

  /** Confidence of the label, or null. */
  public @Nullable Double confidence() {
    return confidence;
  }

  static Sentiment read(JsonNode node, String path) throws Wire.Failure {
    ObjectNode o = Wire.object(node, path);
    return new Sentiment(
        Wire.optEnum(o, "label", path, SentimentLabel::of), Wire.optDouble(o, "confidence", path));
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    Wire.put(o, "label", label == null ? null : label.value());
    Wire.put(o, "confidence", confidence);
    return o;
  }
}
