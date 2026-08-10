package me.finlight.client;

/**
 * Thrown for non-2xx REST responses (after retries). Carries the HTTP status code and the raw
 * response body for inspection.
 */
public class FinlightApiException extends FinlightException {

  private final int statusCode;
  private final String body;

  public FinlightApiException(int statusCode, String body) {
    super("finlight: API error: HTTP " + statusCode);
    this.statusCode = statusCode;
    this.body = body;
  }

  /** The HTTP status code of the failed response. */
  public int statusCode() {
    return statusCode;
  }

  /** The raw response body, possibly empty. */
  public String body() {
    return body;
  }
}
