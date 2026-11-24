package me.finlight.client;

/**
 * Result of {@link Webhooks#constructEvent}: a v3 {@link WebhookEvent}, or a {@link
 * WrongPayloadVersion} for a correctly signed body in the old format (answer that with 2xx too).
 *
 * <pre>{@code
 * WebhookPayload payload = Webhooks.constructEvent(rawBody, headers, secret);
 * if (payload instanceof WebhookEvent event) {
 *   handle(event);
 * } else if (payload instanceof WrongPayloadVersion old) {
 *   keep(old.raw());
 * }
 * }</pre>
 */
public sealed interface WebhookPayload permits WebhookEvent, WrongPayloadVersion {
  /** The body as received. */
  String rawJson();
}
