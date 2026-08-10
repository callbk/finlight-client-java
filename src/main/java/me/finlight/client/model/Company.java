package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A company recognized in an article (entity tagging).
 *
 * @param companyId finlight company id
 * @param confidence tagging confidence between 0 and 1, if available
 * @param country country of the company (ISO 3166-1 alpha-2), if available
 * @param exchange exchange the company is listed on, if available
 * @param industry industry classification, if available
 * @param sector sector classification, if available
 * @param name company name
 * @param ticker primary ticker symbol
 * @param isin primary ISIN, if available
 * @param openfigi OpenFIGI identifier, if available
 * @param primaryListing primary exchange listing, if available
 * @param isins all known ISINs (never null, possibly empty)
 * @param otherListings additional exchange listings (never null, possibly empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Company(
    long companyId,
    @Nullable Double confidence,
    @Nullable String country,
    @Nullable String exchange,
    @Nullable String industry,
    @Nullable String sector,
    String name,
    String ticker,
    @Nullable String isin,
    @Nullable String openfigi,
    @Nullable Listing primaryListing,
    List<String> isins,
    List<Listing> otherListings) {

  public Company {
    isins = isins == null ? List.of() : List.copyOf(isins);
    otherListings = otherListings == null ? List.of() : List.copyOf(otherListings);
  }
}
