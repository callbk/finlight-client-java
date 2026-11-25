package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/** A company tagged in an article. */
public final class Company extends WireObject {
  private final long companyId;
  private final String name;
  private final @Nullable String isin;
  private final @Nullable Listing primaryListing;
  private final @Nullable Double confidence;
  private final @Nullable CompanyRole role;
  private final @Nullable Double relevance;

  Company(
      long companyId,
      String name,
      @Nullable String isin,
      @Nullable Listing primaryListing,
      @Nullable Double confidence,
      @Nullable CompanyRole role,
      @Nullable Double relevance) {
    this.companyId = companyId;
    this.name = name;
    this.isin = isin;
    this.primaryListing = primaryListing;
    this.confidence = confidence;
    this.role = role;
    this.relevance = relevance;
  }

  /** Join key to {@code companies().get(id)} and {@link EventTag#companyIds()}. */
  public long companyId() {
    return companyId;
  }

  /** The company name. */
  public String name() {
    return name;
  }

  /** The ISIN, or null. */
  public @Nullable String isin() {
    return isin;
  }

  /** The primary listing, or null (for example a private company). */
  public @Nullable Listing primaryListing() {
    return primaryListing;
  }

  /** Confidence of the tag, or null. */
  public @Nullable Double confidence() {
    return confidence;
  }

  /** The company's position in the story, or null for articles tagged before roles existed. */
  public @Nullable CompanyRole role() {
    return role;
  }

  /** How much the story is about this company, 0 to 1, or null. */
  public @Nullable Double relevance() {
    return relevance;
  }

  static Company read(JsonNode node, String path) throws Wire.Failure {
    ObjectNode o = Wire.object(node, path);
    return new Company(
        Wire.reqLong(o, "companyId", path),
        Wire.reqString(o, "name", path),
        Wire.optString(o, "isin", path),
        Wire.absentOrNull(o, "primaryListing")
            ? null
            : Listing.read(o.get("primaryListing"), Wire.at(path, "primaryListing")),
        Wire.optDouble(o, "confidence", path),
        Wire.optEnum(o, "role", path, CompanyRole::of),
        Wire.optDouble(o, "relevance", path));
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    o.put("companyId", companyId);
    o.put("name", name);
    Wire.put(o, "isin", isin);
    if (primaryListing == null) {
      o.putNull("primaryListing");
    } else {
      o.set("primaryListing", primaryListing.wire());
    }
    Wire.put(o, "confidence", confidence);
    Wire.put(o, "role", role == null ? null : role.value());
    Wire.put(o, "relevance", relevance);
    return o;
  }
}
