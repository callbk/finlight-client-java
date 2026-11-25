package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/** A listing of a company on an exchange. */
public final class Listing extends WireObject {
  private final @Nullable String ticker;
  private final @Nullable String mic;
  private final @Nullable String country;
  private final @Nullable Boolean active;
  private final boolean activeSent;

  Listing(
      @Nullable String ticker,
      @Nullable String mic,
      @Nullable String country,
      @Nullable Boolean active,
      boolean activeSent) {
    this.ticker = ticker;
    this.mic = mic;
    this.country = country;
    this.active = active;
    this.activeSent = activeSent;
  }

  /** The ticker symbol. */
  public @Nullable String ticker() {
    return ticker;
  }

  /** The exchange as ISO 10383 MIC, or null. */
  public @Nullable String mic() {
    return mic;
  }

  /** The country of the exchange, or null. */
  public @Nullable String country() {
    return country;
  }

  /** Whether the listing is still traded. Only set in {@link CompanyRecord#listings()}. */
  public @Nullable Boolean active() {
    return active;
  }

  static Listing read(JsonNode node, String path) throws Wire.Failure {
    ObjectNode o = Wire.object(node, path);
    return new Listing(
        Wire.optString(o, "ticker", path),
        Wire.optString(o, "mic", path),
        Wire.optString(o, "country", path),
        Wire.optBool(o, "active", path),
        o.has("active"));
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    Wire.put(o, "ticker", ticker);
    Wire.put(o, "mic", mic);
    Wire.put(o, "country", country);
    if (activeSent) {
      Wire.put(o, "active", active);
    }
    return o;
  }
}
