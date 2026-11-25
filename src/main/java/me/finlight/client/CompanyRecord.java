package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Master data of a company from {@code companies()}. */
public final class CompanyRecord extends WireObject {
  private final long companyId;
  private final String name;
  private final @Nullable String country;
  private final @Nullable String sector;
  private final @Nullable String industry;
  private final List<String> isins;
  private final @Nullable Listing primaryListing;
  private final List<Listing> listings;
  private final @Nullable String updatedAt;
  private final String rawJson;
  private final @Nullable CacheInfo cacheInfo;

  private CompanyRecord(CompanyRecord r, @Nullable CacheInfo cacheInfo) {
    this.companyId = r.companyId;
    this.name = r.name;
    this.country = r.country;
    this.sector = r.sector;
    this.industry = r.industry;
    this.isins = r.isins;
    this.primaryListing = r.primaryListing;
    this.listings = r.listings;
    this.updatedAt = r.updatedAt;
    this.rawJson = r.rawJson;
    this.cacheInfo = cacheInfo;
  }

  private CompanyRecord(ObjectNode o, String path) throws Wire.Failure {
    this.companyId = Wire.reqLong(o, "companyId", path);
    this.name = Wire.reqString(o, "name", path);
    this.country = Wire.optString(o, "country", path);
    this.sector = Wire.optString(o, "sector", path);
    this.industry = Wire.optString(o, "industry", path);
    this.isins = Wire.list(o, "isins", path, Wire::string);
    this.primaryListing =
        Wire.absentOrNull(o, "primaryListing")
            ? null
            : Listing.read(o.get("primaryListing"), Wire.at(path, "primaryListing"));
    this.listings = Wire.list(o, "listings", path, Listing::read);
    this.updatedAt = Wire.optString(o, "updatedAt", path);
    this.rawJson = Json.write(o);
    this.cacheInfo = null;
  }

  static CompanyRecord read(JsonNode node, String path) throws Wire.Failure {
    return new CompanyRecord(Wire.object(node, path), path);
  }

  CompanyRecord withCacheInfo(@Nullable CacheInfo info) {
    return new CompanyRecord(this, info);
  }

  /** The company id. */
  public long companyId() {
    return companyId;
  }

  /** The company name. */
  public String name() {
    return name;
  }

  /** Country of domicile, or null. */
  public @Nullable String country() {
    return country;
  }

  /** GICS sector, or null. */
  public @Nullable String sector() {
    return sector;
  }

  /** Industry, or null. */
  public @Nullable String industry() {
    return industry;
  }

  /** ISINs; possibly empty. */
  public List<String> isins() {
    return isins;
  }

  /** The primary listing, or null. */
  public @Nullable Listing primaryListing() {
    return primaryListing;
  }

  /** One listing per exchange, the primary one first; possibly empty. */
  public List<Listing> listings() {
    return listings;
  }

  /** Last update of the record, as sent. */
  public @Nullable String updatedAt() {
    return updatedAt;
  }

  /** {@link #updatedAt()} parsed strictly (RFC 3339), or null. */
  public @Nullable Instant updatedAtTime() {
    return Times.parse(updatedAt);
  }

  /** ETag data when the record came from {@code companies().get(id)}; null otherwise. */
  public @Nullable CacheInfo cacheInfo() {
    return cacheInfo;
  }

  /** The record JSON as received, including keys this SDK version does not know. */
  public String rawJson() {
    return rawJson;
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    o.put("companyId", companyId);
    o.put("name", name);
    Wire.put(o, "country", country);
    Wire.put(o, "sector", sector);
    Wire.put(o, "industry", industry);
    o.set("isins", Wire.strings(isins));
    if (primaryListing == null) {
      o.putNull("primaryListing");
    } else {
      o.set("primaryListing", primaryListing.wire());
    }
    ArrayNode a = o.putArray("listings");
    listings.forEach(l -> a.add(l.wire()));
    Wire.put(o, "updatedAt", updatedAt);
    return o;
  }
}
