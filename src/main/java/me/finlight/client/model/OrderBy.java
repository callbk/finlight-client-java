package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Sort field for article queries. */
public enum OrderBy {
  PUBLISH_DATE("publishDate"),
  CREATED_AT("createdAt"),
  REVISED_DATE("revisedDate");

  private final String value;

  OrderBy(String value) {
    this.value = value;
  }

  /** The wire value sent to the API. */
  @JsonValue
  public String value() {
    return value;
  }
}
