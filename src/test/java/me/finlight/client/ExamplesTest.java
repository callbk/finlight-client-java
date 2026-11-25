package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** R-TEST-1: the five standard examples run against the local mocks. */
@Timeout(60)
class ExamplesTest {
  private static Method method(String cls, String name, Class<?>... types) throws Exception {
    Method m = Class.forName("me.finlight.examples." + cls).getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m;
  }

  private static String run(Method m, Object... args) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Object[] all = new Object[args.length + 1];
    all[0] = args[0];
    all[1] = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    System.arraycopy(args, 1, all, 2, args.length - 1);
    m.invoke(null, all);
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void search() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(Kit.load("fixtures/requests/search-all-parameters.json").get("response"));
      String out = run(method("Search", "run", FinlightClient.class, PrintStream.class), client);
      assertEquals(
          Kit.parse(
              "{\"query\":\"ticker:NVDA\",\"languages\":[\"en\"],\"include\":[\"companies\",\"events\"],\"limit\":10}"),
          mock.requests.get(0).json());
      assertTrue(out.contains("more pages:"), out);
      mock.enqueue(Kit.load("fixtures/errors/not-found-id.json").get("response"));
      String err = run(method("Search", "run", FinlightClient.class, PrintStream.class), client);
      assertTrue(err.contains("API error 404 NOT_FOUND"), err);
      client.close();
    }
  }

  @Test
  void searchAll() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      JsonNode f = Kit.load("fixtures/pagination/three-pages.json");
      f.get("steps").forEach(s -> mock.enqueue(s.get("response")));
      String out = run(method("SearchAll", "run", FinlightClient.class, PrintStream.class), client);
      assertEquals(3, mock.requests.size());
      assertTrue(out.contains("5 articles from 3 pages"), out);
      client.close();
    }
  }

  @Test
  void lookup() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      JsonNode byLink = Kit.load("fixtures/requests/get-by-link.json").get("response");
      mock.enqueue(byLink)
          .enqueue(Kit.load("fixtures/errors/not-found-id.json").get("response"))
          .enqueue(Kit.load("fixtures/requests/companies-search.json").get("response"))
          .enqueue(Kit.load("fixtures/requests/companies-get.json").get("response"))
          .enqueue(Kit.load("fixtures/requests/sources-list-full.json").get("response"));
      String out =
          run(
              method("Lookup", "run", FinlightClient.class, PrintStream.class, String.class),
              client,
              byLink.at("/body/article/link").asText());
      assertEquals(
          List.of(
              "/v3/articles/by-link",
              "/v3/articles/" + byLink.at("/body/article/id").asText(),
              "/v3/companies",
              "/v3/companies/900001",
              "/v3/sources"),
          mock.requests.stream().map(HttpMock.Recorded::path).toList());
      assertTrue(out.contains("not found"), out);
      assertTrue(out.contains("etag"), out);
      client.close();
    }
  }

  @Test
  void stream() throws Exception {
    JsonNode article = Kit.load("fixtures/decode/full-article.json").get("wire");
    try (WsServer ws = new WsServer()) {
      FinlightClient client =
          new FinlightClient(FinlightConfig.builder(Kit.API_KEY).wssUrl(ws.url()).build());
      Method m = method("Stream", "run", FinlightClient.class, PrintStream.class, int.class);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      Thread t =
          new Thread(
              () -> {
                try {
                  m.invoke(null, client, new PrintStream(bytes, true, StandardCharsets.UTF_8), 2);
                } catch (ReflectiveOperationException e) {
                  throw new IllegalStateException(e);
                }
              });
      t.start();
      WsServer.Conn c = ws.nextDial("dial").conn();
      JsonNode sub = c.nextSubscribe();
      assertEquals(Kit.parse("[\"companies\"]"), sub.get("include"));
      c.send(
          "{\"event\":\"connection.admitted\",\"payloadVersion\":3,\"data\":{\"leaseId\":\"l\",\"serverNow\":1,\"clientNonce\":null}}");
      c.send("{\"event\":\"connection.draining\",\"payloadVersion\":3,\"data\":{}}");
      for (String id : new String[] {"a1", "a2", "a3"}) {
        com.fasterxml.jackson.databind.node.ObjectNode a = article.deepCopy();
        a.put("id", id);
        c.send("{\"event\":\"article.created\",\"payloadVersion\":3,\"data\":" + a + "}");
      }
      t.join(10_000);
      String out = bytes.toString(StandardCharsets.UTF_8);
      assertTrue(out.contains("admitted, lease l"), out);
      assertEquals(2, out.lines().filter(l -> l.startsWith("created: ")).count(), out);
      client.close();
    }
  }

  @Test
  void webhookEndpoint() throws Exception {
    Class<?> handler = Class.forName("me.finlight.examples.WebhookEndpoint$Handler");
    List<Object> handled = new CopyOnWriteArrayList<>();
    Object h =
        java.lang.reflect.Proxy.newProxyInstance(
            handler.getClassLoader(), new Class<?>[] {handler}, (p, mm, a) -> handled.add(a[0]));
    Method start = method("WebhookEndpoint", "start", int.class, String.class, handler);
    com.sun.net.httpserver.HttpServer server =
        (com.sun.net.httpserver.HttpServer) start.invoke(null, 0, Support.SECRET, h);
    try {
      HttpClient http = HttpClient.newHttpClient();
      URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/finlight");
      JsonNode event =
          Kit.load("fixtures/forward-compat/envelope-keys.json").at("/envelopes/2/wire");
      String ts = java.time.Instant.now().toString();
      byte[] body = event.toString().getBytes(StandardCharsets.UTF_8);
      HttpRequest ok =
          HttpRequest.newBuilder(uri)
              .header("X-Webhook-Signature", Support.sign(Support.SECRET, ts, body))
              .header("X-Webhook-Timestamp", ts)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      assertEquals(204, http.send(ok, HttpResponse.BodyHandlers.discarding()).statusCode());
      assertEquals(
          204,
          http.send(ok, HttpResponse.BodyHandlers.discarding()).statusCode(),
          "a repeat is acknowledged");
      StreamProbe.waitFor(() -> handled.size() == 1, "the event handled once");
      HttpRequest bad =
          HttpRequest.newBuilder(uri)
              .header("X-Webhook-Signature", "sha256=00")
              .header("X-Webhook-Timestamp", ts)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      assertEquals(400, http.send(bad, HttpResponse.BodyHandlers.discarding()).statusCode());
      JsonNode v2 = Kit.load("fixtures/webhook/v2-body.json");
      byte[] old = v2.at("/request/rawBody").asText().getBytes(StandardCharsets.UTF_8);
      HttpRequest wrong =
          HttpRequest.newBuilder(uri)
              .header("X-Webhook-Signature", Support.sign(Support.SECRET, ts, old))
              .header("X-Webhook-Timestamp", ts)
              .POST(HttpRequest.BodyPublishers.ofByteArray(old))
              .build();
      assertEquals(
          204,
          http.send(wrong, HttpResponse.BodyHandlers.discarding()).statusCode(),
          "2xx for a v2 body");
      assertEquals(1, handled.size());
    } finally {
      server.stop(0);
    }
  }
}
