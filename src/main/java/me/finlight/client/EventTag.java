package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** An event tagged in an article. */
public final class EventTag extends WireObject {
  private final EventType type;
  private final @Nullable EventDirection direction;
  private final @Nullable Importance importance;
  private final @Nullable Double confidence;
  private final Gated<List<Long>> companyIds;

  EventTag(
      EventType type,
      @Nullable EventDirection direction,
      @Nullable Importance importance,
      @Nullable Double confidence,
      Gated<List<Long>> companyIds) {
    this.type = type;
    this.direction = direction;
    this.importance = importance;
    this.confidence = confidence;
    this.companyIds = companyIds;
  }

  /** The event type. */
  public EventType type() {
    return type;
  }

  /** The direction, or null. */
  public @Nullable EventDirection direction() {
    return direction;
  }

  /** The importance, or null. */
  public @Nullable Importance importance() {
    return importance;
  }

  /** Confidence of the tag, or null. */
  public @Nullable Double confidence() {
    return confidence;
  }

  /**
   * The companies involved. Absent unless {@code companies} is in the same payload (requested and
   * entitled).
   */
  public Gated<List<Long>> companyIds() {
    return companyIds;
  }

  static EventTag read(JsonNode node, String path) throws Wire.Failure {
    ObjectNode o = Wire.object(node, path);
    return new EventTag(
        EventType.of(Wire.reqString(o, "type", path)),
        Wire.optEnum(o, "direction", path, EventDirection::of),
        Wire.optEnum(o, "importance", path, Importance::of),
        Wire.optDouble(o, "confidence", path),
        Wire.gatedList(o, "companyIds", path, Wire::integer));
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    o.put("type", type.value());
    Wire.put(o, "direction", direction == null ? null : direction.value());
    Wire.put(o, "importance", importance == null ? null : importance.value());
    Wire.put(o, "confidence", confidence);
    List<Long> ids = companyIds.orNull();
    if (ids != null) {
      ArrayNode a = o.putArray("companyIds");
      ids.forEach(a::add);
    }
    return o;
  }
}
