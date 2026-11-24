package me.finlight.client;

/** A webhook failed verification. Answer it with 400, never with 410. */
public final class WebhookVerificationException extends FinlightException {
  private static final long serialVersionUID = 1L;

  private final WebhookVerificationReason reason;

  WebhookVerificationException(WebhookVerificationReason reason, String message) {
    super(message, null);
    this.reason = reason;
  }

  /** Why the delivery was rejected. */
  public WebhookVerificationReason reason() {
    return reason;
  }

  @Override
  public FinlightErrorKind kind() {
    return FinlightErrorKind.WEBHOOK_VERIFICATION;
  }
}
