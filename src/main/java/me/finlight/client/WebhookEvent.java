package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A v3 webhook delivery. Deliveries can repeat: deduplicate on {@link #eventId()}. */
public final class WebhookEvent implements WebhookPayload {
  private final String eventId;
  private final WebhookEventName event;
  private final @Nullable String occurredAt;
  private final int payloadVersion;
  private final @Nullable String subscriptionId;
  private final @Nullable List<String> changedFields;
  private final Article data;
  private final String rawJson;

  private WebhookEvent(ObjectNode o) throws Wire.Failure {
    this.eventId = Wire.reqString(o, "eventId", "");
    this.event = WebhookEventName.of(Wire.reqString(o, "event", ""));
    this.occurredAt = Wire.optString(o, "occurredAt", "");
    this.payloadVersion = FinlightClient.PAYLOAD_VERSION;
    this.subscriptionId = Wire.optString(o, "subscriptionId", "");
    this.changedFields =
        Wire.absentOrNull(o, "changedFields")
            ? null
            : Wire.elements(o.get("changedFields"), "changedFields", Wire::string);
    this.data = Article.read(o.get("data"), "data");
    this.rawJson = Json.write(o);
  }

  static WebhookEvent read(JsonNode node) throws Wire.Failure {
    return new WebhookEvent(Wire.object(node, ""));
  }

  /** Idempotency key: the same on every retry of this event. */
  public String eventId() {
    return eventId;
  }

  /** {@code article.created}, {@code article.updated} or a newer name. */
  public WebhookEventName event() {
    return event;
  }

  /** When the event happened, as sent. Stable across retries; never the delivery time. */
  public @Nullable String occurredAt() {
    return occurredAt;
  }

  /** {@link #occurredAt()} parsed strictly (RFC 3339), or null. */
  public @Nullable Instant occurredAtTime() {
    return Times.parse(occurredAt);
  }

  /** Always 3. */
  public int payloadVersion() {
    return payloadVersion;
  }

  /** The webhook id. */
  public @Nullable String subscriptionId() {
    return subscriptionId;
  }

  /** Fields that changed on {@code article.updated}; null when the key was not sent. */
  public @Nullable List<String> changedFields() {
    return changedFields;
  }

  /** The article, cut by your plan at send time. */
  public Article data() {
    return data;
  }

  /** True for the dashboard test webhook ({@code eventId} starts with {@code evt_test}). */
  public boolean isTest() {
    return eventId.startsWith("evt_test");
  }

  @Override
  public String rawJson() {
    return rawJson;
  }

  @Override
  public String toString() {
    return "WebhookEvent{eventId=" + eventId + ", event=" + event + ", link=" + data.link() + "}";
  }
}
