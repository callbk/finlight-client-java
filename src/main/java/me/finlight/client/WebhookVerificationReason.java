package me.finlight.client;

/** Why {@link Webhooks} rejected a delivery. */
public enum WebhookVerificationReason {
  /** The body was missing. Pass the raw bytes or their UTF-8 text, never parsed JSON. */
  INVALID_RAW_BODY("invalid_raw_body"),
  /** The secret was missing or empty. */
  INVALID_SECRET("invalid_secret"),
  /** No {@code X-Webhook-Signature} header. */
  MISSING_SIGNATURE("missing_signature"),
  /** No {@code X-Webhook-Timestamp} header. */
  MISSING_TIMESTAMP("missing_timestamp"),
  /** A webhook header was given more than once. */
  DUPLICATE_HEADER("duplicate_header"),
  /** A webhook header holds characters outside ASCII. */
  NON_ASCII_HEADER("non_ascii_header"),
  /** The signature does not start with {@code sha256=}. */
  UNSUPPORTED_SIGNATURE_SCHEME("unsupported_signature_scheme"),
  /** The timestamp is not an RFC 3339 time with a zone. */
  UNPARSEABLE_TIMESTAMP("unparseable_timestamp"),
  /** The signature does not match the body. */
  SIGNATURE_MISMATCH("signature_mismatch"),
  /** The timestamp is further away from now than the tolerance. */
  TIMESTAMP_OUTSIDE_TOLERANCE("timestamp_outside_tolerance");

  private final String value;

  WebhookVerificationReason(String value) {
    this.value = value;
  }

  /** The reason as text, for example {@code "signature_mismatch"}. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
