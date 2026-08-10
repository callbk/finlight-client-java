package me.finlight.client;

/**
 * Thrown by {@link WebhookService#constructEvent} when a webhook fails signature, timestamp, or
 * payload validation.
 */
public class WebhookVerificationException extends FinlightException {

  public WebhookVerificationException(String message) {
    super(message);
  }
}
