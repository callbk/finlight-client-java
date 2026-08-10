package me.finlight.client.support;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * In-process WebSocket server for protocol tests, mirroring the sibling clients' test servers.
 * Handlers run on the server's connection threads; failures surface via {@link #errors}.
 */
public final class WsTestServer extends WebSocketServer implements AutoCloseable {

  /** Handler for an accepted connection. */
  public interface OpenHandler {
    void accept(WebSocket conn, ClientHandshake handshake) throws Exception;
  }

  /** Handler for a received text message. */
  public interface MessageHandler {
    void accept(WebSocket conn, String message) throws Exception;
  }

  private final CountDownLatch started = new CountDownLatch(1);
  private final OpenHandler openHandler;
  private final MessageHandler messageHandler;

  /** Resource paths of accepted connections, in order. */
  public final List<String> resourcePaths = new CopyOnWriteArrayList<>();

  /** 1-based connection index per open socket. */
  public final ConcurrentHashMap<WebSocket, Integer> connectionIndex = new ConcurrentHashMap<>();

  /** Exceptions thrown by handlers. */
  public final List<Exception> errors = new CopyOnWriteArrayList<>();

  private final AtomicInteger connections = new AtomicInteger();

  private WsTestServer(OpenHandler openHandler, MessageHandler messageHandler) {
    super(new InetSocketAddress("127.0.0.1", 0));
    setReuseAddr(true);
    this.openHandler = openHandler;
    this.messageHandler = messageHandler;
  }

  /** Starts a server; blocks until it accepts connections. */
  public static WsTestServer open(OpenHandler onOpen, MessageHandler onMessage)
      throws InterruptedException {
    WsTestServer server = new WsTestServer(onOpen, onMessage);
    server.start();
    if (!server.started.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("test WebSocket server did not start");
    }
    return server;
  }

  /** The server's ws:// base URL. */
  public String url() {
    return "ws://127.0.0.1:" + getPort();
  }

  /** A sendArticle message for the given link. */
  public static String articleMessage(String link) {
    return """
        {"action":"sendArticle","data":{"link":"%s","title":"T","publishDate":\
        "2025-06-01T12:00:00Z","source":"example.com","language":"en"}}"""
        .formatted(link);
  }

  @Override
  public void onStart() {
    started.countDown();
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    connectionIndex.put(conn, connections.incrementAndGet());
    resourcePaths.add(handshake.getResourceDescriptor());
    try {
      openHandler.accept(conn, handshake);
    } catch (Exception e) {
      errors.add(e);
    }
  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    try {
      messageHandler.accept(conn, message);
    } catch (Exception e) {
      errors.add(e);
    }
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

  @Override
  public void onError(WebSocket conn, Exception ex) {}

  @Override
  public void close() throws InterruptedException {
    stop(1000);
  }
}
