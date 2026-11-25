package me.finlight.examples;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.finlight.client.WebhookEvent;
import me.finlight.client.WebhookPayload;
import me.finlight.client.WebhookVerificationException;
import me.finlight.client.Webhooks;
import me.finlight.client.WrongPayloadVersion;

/**
 * A webhook receiver with the JDK's HTTP server. It verifies the raw body, answers 2xx at once and
 * does the work afterwards. Never answer with 410: it disables the webhook.
 */
public final class WebhookEndpoint {
  private WebhookEndpoint() {}

  public static void main(String[] args) throws IOException {
    String secret = System.getenv("FINLIGHT_WEBHOOK_SECRET");
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("set FINLIGHT_WEBHOOK_SECRET");
    }
    HttpServer server = start(8080, secret, event -> System.out.println("handled " + event));
    System.out.println(
        "listening on http://localhost:" + server.getAddress().getPort() + "/finlight");
  }

  /** What to do with a verified event; runs after the response went out. */
  interface Handler {
    void handle(WebhookEvent event);
  }

  static HttpServer start(int port, String secret, Handler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    ExecutorService work = Executors.newSingleThreadExecutor();
    // Deliveries can repeat: remember the eventIds you processed (use a store in production).
    Set<String> seen =
        Collections.synchronizedSet(
            Collections.newSetFromMap(
                new LinkedHashMap<>() {
                  private static final long serialVersionUID = 1L;

                  @Override
                  protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 10_000;
                  }
                }));
    server.createContext(
        "/finlight",
        exchange -> {
          try (InputStream in = exchange.getRequestBody()) {
            byte[] rawBody = in.readAllBytes(); // the raw bytes, never re-serialized JSON
            WebhookPayload payload =
                Webhooks.constructEvent(rawBody, exchange.getRequestHeaders(), secret);
            if (payload instanceof WrongPayloadVersion old) {
              // An old-format delivery (queued before the switch to v3): answer 2xx, keep the data.
              System.out.println(
                  "old payload for " + old.link() + ": " + old.raw().length() + " bytes kept");
              respond(exchange, 204);
              return;
            }
            WebhookEvent event = (WebhookEvent) payload;
            respond(exchange, 204); // within 5 s, before the work
            if (seen.add(event.eventId())) {
              work.execute(() -> handler.handle(event));
            }
          } catch (WebhookVerificationException e) {
            respond(exchange, 400);
          }
        });
    server.start();
    return server;
  }

  private static void respond(HttpExchange exchange, int status) throws IOException {
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }
}
