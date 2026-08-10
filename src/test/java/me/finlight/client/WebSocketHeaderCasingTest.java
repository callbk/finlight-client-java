package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import me.finlight.client.model.GetArticlesWebSocketParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The wss.finlight.me authorizer reads the API key header case-sensitively in exact lowercase; a
 * canonicalized {@code X-Api-Key} is rejected with 401. This test captures the raw upgrade request
 * and asserts the header names go out in lowercase.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WebSocketHeaderCasingTest {

  @Test
  void sendsApiKeyHeaderInExactLowercase() throws Exception {
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      serverSocket.setSoTimeout(10_000);

      ArticleWebSocketClient client =
          new ArticleWebSocketClient(
              FinlightConfig.builder("test-key")
                  .wssUrl("ws://127.0.0.1:" + serverSocket.getLocalPort())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              WebSocketOptions.builder()
                  .baseReconnectDelay(Duration.ofMillis(10))
                  .takeover(true)
                  .build());
      var future = client.connectAsync(GetArticlesWebSocketParams.builder().build(), article -> {});

      String requestHead;
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(10_000);
        requestHead = readHead(socket.getInputStream());
        client.stop();
        OutputStream out = socket.getOutputStream();
        out.write(
            "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
      future.get(15, TimeUnit.SECONDS);

      assertTrue(
          requestHead.contains("\r\nx-api-key: test-key"),
          "x-api-key must be sent in exact lowercase; request was:\n" + requestHead);
      assertTrue(requestHead.contains("\r\nx-client-version: java/finlight-client@"));
      assertTrue(requestHead.contains("\r\nx-takeover: true"));
      assertFalse(requestHead.contains("X-Api-Key"), "header name must not be canonicalized");
      assertFalse(requestHead.contains("X-API-KEY"), "header name must not be canonicalized");
    }
  }

  private static String readHead(InputStream in) throws Exception {
    StringBuilder head = new StringBuilder();
    int b;
    while ((b = in.read()) != -1) {
      head.append((char) b);
      if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) {
        break;
      }
    }
    return head.toString();
  }
}
