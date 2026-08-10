package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One exchange listing of a company.
 *
 * @param ticker ticker symbol on the exchange
 * @param exchangeCode exchange code (e.g. {@code XNAS})
 * @param exchangeCountry country of the exchange (ISO 3166-1 alpha-2)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Listing(String ticker, String exchangeCode, String exchangeCountry) {}
