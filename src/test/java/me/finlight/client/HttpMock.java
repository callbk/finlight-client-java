package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/** A real HTTP server on 127.0.0.1 that answers queued responses and records every request. */
final class HttpMock implements AutoCloseable {
  record Recorded(
      String method,
      String path,
      String rawQuery,
      Map<String, List<String>> query,
      Map<String, String> headers,
      byte[] body) {
    String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }

    JsonNode json() {
      return Kit.parse(bodyText());
    }

    String header(String name) {
      return headers.get(name.toLowerCase(java.util.Locale.ROOT));
    }
  }

  record Canned(int status, Map<String, String> headers, byte[] body) {}

  private final HttpServer server;
  final List<Recorded> requests = new CopyOnWriteArrayList<>();
  private final ConcurrentLinkedDeque<Canned> queue = new ConcurrentLinkedDeque<>();

  HttpMock() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        ex -> {
          byte[] body = ex.getRequestBody().readAllBytes();
          Map<String, String> headers = new TreeMap<>();
          ex.getRequestHeaders()
              .forEach(
                  (k, v) -> headers.put(k.toLowerCase(java.util.Locale.ROOT), String.join(",", v)));
          String rawQuery = ex.getRequestURI().getRawQuery();
          requests.add(
              new Recorded(
                  ex.getRequestMethod(),
                  ex.getRequestURI().getRawPath(),
                  rawQuery == null ? "" : rawQuery,
                  decode(rawQuery),
                  headers,
                  body));
          Canned c = queue.poll();
          if (c == null) {
            c = new Canned(599, Map.of(), "no response queued".getBytes(StandardCharsets.UTF_8));
          }
          c.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
          if (c.status() == 304 || c.body().length == 0) {
            ex.sendResponseHeaders(c.status(), -1);
          } else {
            ex.sendResponseHeaders(c.status(), c.body().length);
            try (OutputStream out = ex.getResponseBody()) {
              out.write(c.body());
            }
          }
          ex.close();
        });
    server.start();
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Queues a response given in the kit form {status, headers, body}. */
  HttpMock enqueue(JsonNode response) {
    int status = response.get("status").asInt();
    Map<String, String> headers = new LinkedHashMap<>();
    JsonNode h = response.get("headers");
    if (h != null) {
      h.fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
    }
    JsonNode body = response.get("body");
    byte[] bytes;
    if (body == null || body.isNull()) {
      bytes = new byte[0];
    } else if (body.isTextual()) {
      bytes = body.textValue().getBytes(StandardCharsets.UTF_8);
    } else {
      bytes = body.toString().getBytes(StandardCharsets.UTF_8);
    }
    queue.add(new Canned(status, headers, bytes));
    return this;
  }

  HttpMock enqueue(int status, String contentType, String body) {
    queue.add(
        new Canned(
            status, Map.of("content-type", contentType), body.getBytes(StandardCharsets.UTF_8)));
    return this;
  }

  void reset() {
    requests.clear();
    queue.clear();
  }

  static Map<String, List<String>> decode(String raw) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    if (raw == null || raw.isEmpty()) {
      return out;
    }
    for (String pair : raw.split("&")) {
      int i = pair.indexOf('=');
      String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
      String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
      out.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
    }
    return out;
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
