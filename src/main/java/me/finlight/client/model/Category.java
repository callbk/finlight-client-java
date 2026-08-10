package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** An article category assigned by finlight's classification. */
public enum Category {
  MARKETS("markets"),
  ECONOMY("economy"),
  BUSINESS("business"),
  POLITICS("politics"),
  GEOPOLITICS("geopolitics"),
  REGULATION("regulation"),
  TECHNOLOGY("technology"),
  ENERGY("energy"),
  COMMODITIES("commodities"),
  CRYPTO("crypto"),
  HEALTH("health"),
  CLIMATE("climate"),
  SECURITY("security");

  private final String value;

  Category(String value) {
    this.value = value;
  }

  /** The wire value sent to the API. */
  @JsonValue
  public String value() {
    return value;
  }
}
