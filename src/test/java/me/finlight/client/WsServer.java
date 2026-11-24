package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * An in-process WebSocket server on raw sockets. It sees the exact request bytes (header casing),
 * can refuse a dial with any status and body, and speaks enough RFC 6455 for the client.
 */
final class WsServer implements AutoCloseable {
  record Refusal(int status, String body) {}

  /** One dial: the raw request head and, when accepted, the connection. */
  record Dial(String head, String path, Map<String, String> headers, Conn conn) {}

  private final ServerSocket socket;
  private final Thread acceptor;
  final BlockingQueue<Dial> dials = new LinkedBlockingQueue<>();
  final ConcurrentLinkedQueue<Refusal> script = new ConcurrentLinkedQueue<>();
  final List<Conn> conns = new CopyOnWriteArrayList<>();
  volatile boolean autoPong = true;
  volatile int dialCount;

  WsServer() throws IOException {
    socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    acceptor = new Thread(this::acceptLoop, "ws-test-server");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  String url() {
    return "ws://127.0.0.1:" + socket.getLocalPort();
  }

  Dial nextDial(String what) throws InterruptedException {
    Dial d = dials.poll(5, TimeUnit.SECONDS);
    if (d == null) {
      throw new AssertionError("timed out waiting for " + what);
    }
    return d;
  }

  long openConnections() {
    return conns.stream().filter(c -> c.open).count();
  }

  private void acceptLoop() {
    while (!socket.isClosed()) {
      try {
        Socket s = socket.accept();
        Thread t = new Thread(() -> handle(s), "ws-test-conn");
        t.setDaemon(true);
        t.start();
      } catch (IOException e) {
        return;
      }
    }
  }

  private void handle(Socket s) {
    try {
      s.setTcpNoDelay(true);
      InputStream in = s.getInputStream();
      String head = readHead(in);
      dialCount++;
      String requestLine = head.substring(0, head.indexOf("\r\n"));
      String path = requestLine.split(" ")[1];
      Map<String, String> headers = new LinkedHashMap<>();
      for (String line : head.split("\r\n")) {
        int i = line.indexOf(':');
        if (i > 0) {
          headers.put(
              line.substring(0, i).trim().toLowerCase(Locale.ROOT), line.substring(i + 1).trim());
        }
      }
      Refusal r = script.poll();
      OutputStream out = s.getOutputStream();
      if (r != null && r.status() != 101) {
        byte[] body = r.body().getBytes(StandardCharsets.UTF_8);
        out.write(
            ("HTTP/1.1 "
                    + r.status()
                    + " Refused\r\nContent-Type: application/json\r\nContent-Length: "
                    + body.length
                    + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
        dials.add(new Dial(head, path, headers, null));
        Thread.sleep(50);
        s.close();
        return;
      }
      String key = headers.get("sec-websocket-key");
      String accept =
          Base64.getEncoder()
              .encodeToString(
                  MessageDigest.getInstance("SHA-1")
                      .digest(
                          (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                              .getBytes(StandardCharsets.US_ASCII)));
      out.write(
          ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  + "Sec-WebSocket-Accept: "
                  + accept
                  + "\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.flush();
      Conn c = new Conn(s);
      conns.add(c);
      dials.add(new Dial(head, path, headers, c));
      c.readLoop(new DataInputStream(in));
    } catch (Exception e) {
      try {
        s.close();
      } catch (IOException ignored) {
        // closing anyway
      }
    }
  }

  private static String readHead(InputStream in) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    int last4 = 0;
    while (true) {
      int x = in.read();
      if (x < 0) {
        throw new EOFException();
      }
      b.write(x);
      last4 = (last4 << 8) | x;
      if (last4 == 0x0d0a0d0a) {
        return b.toString(StandardCharsets.ISO_8859_1);
      }
    }
  }

  @Override
  public void close() throws IOException {
    for (Conn c : conns) {
      c.kill();
    }
    socket.close();
  }

  /** One accepted connection. */
  final class Conn {
    private final Socket s;
    final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
    final List<JsonNode> received = new CopyOnWriteArrayList<>();
    volatile boolean open = true;
    volatile int closeCodeFromClient = -1;

    Conn(Socket s) {
      this.s = s;
    }

    /** The next client frame that matches; frames before it are skipped. */
    JsonNode next(String what, Predicate<JsonNode> match) throws InterruptedException {
      long end = System.currentTimeMillis() + 5000;
      while (System.currentTimeMillis() < end) {
        JsonNode n = inbox.poll(50, TimeUnit.MILLISECONDS);
        if (n != null && match.test(n)) {
          return n;
        }
      }
      throw new AssertionError("timed out waiting for " + what);
    }

    /** The next client frame that is not a ping. */
    JsonNode nextSubscribe() throws InterruptedException {
      return next("subscribe frame", n -> !"ping".equals(n.path("action").asText()));
    }

    void send(JsonNode frame) {
      send(frame.toString());
    }

    synchronized void send(String text) {
      write(0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Server-initiated close (as API Gateway DeleteConnection would). */
    synchronized void close(int code) {
      if (!open) {
        return;
      }
      write(0x8, new byte[] {(byte) (code >> 8), (byte) code});
      open = false;
      try {
        s.shutdownOutput();
      } catch (IOException ignored) {
        // already gone
      }
    }

    void kill() {
      open = false;
      try {
        s.close();
      } catch (IOException ignored) {
        // already gone
      }
    }

    private synchronized void write(int opcode, byte[] payload) {
      try {
        OutputStream out = s.getOutputStream();
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | opcode);
        if (payload.length < 126) {
          f.write(payload.length);
        } else if (payload.length < 65536) {
          f.write(126);
          f.write(payload.length >> 8);
          f.write(payload.length);
        } else {
          f.write(127);
          for (int i = 7; i >= 0; i--) {
            f.write((int) ((long) payload.length >> (8 * i)));
          }
        }
        f.write(payload);
        out.write(f.toByteArray());
        out.flush();
      } catch (IOException e) {
        open = false;
      }
    }

    void readLoop(DataInputStream in) {
      ByteArrayOutputStream message = new ByteArrayOutputStream();
      try {
        while (true) {
          int b0 = in.readUnsignedByte();
          int b1 = in.readUnsignedByte();
          int opcode = b0 & 0x0f;
          long len = b1 & 0x7f;
          if (len == 126) {
            len = in.readUnsignedShort();
          } else if (len == 127) {
            len = in.readLong();
          }
          byte[] mask = new byte[4];
          if ((b1 & 0x80) != 0) {
            in.readFully(mask);
          }
          byte[] payload = new byte[(int) len];
          in.readFully(payload);
          for (int i = 0; i < payload.length; i++) {
            payload[i] ^= mask[i % 4];
          }
          if (opcode == 0x8) {
            closeCodeFromClient =
                payload.length >= 2 ? ((payload[0] & 0xff) << 8) | (payload[1] & 0xff) : 1005;
            if (open) {
              write(0x8, payload.length >= 2 ? new byte[] {payload[0], payload[1]} : new byte[0]);
            }
            break;
          } else if (opcode == 0x9) {
            write(0xA, payload);
          } else if (opcode == 0x1 || opcode == 0x0) {
            message.write(payload);
            if ((b0 & 0x80) != 0) {
              String text = message.toString(StandardCharsets.UTF_8);
              message.reset();
              JsonNode node = Kit.parse(text);
              received.add(node);
              if (autoPong && "ping".equals(node.path("action").asText()) && open) {
                send(
                    "{\"event\":\"connection.pong\",\"payloadVersion\":3,\"data\":{\"t\":"
                        + node.path("t").asLong()
                        + ",\"serverNow\":1}}");
              }
              inbox.add(node);
            }
          }
        }
      } catch (IOException e) {
        // the client went away
      } finally {
        open = false;
        try {
          s.close();
        } catch (IOException ignored) {
          // already closed
        }
      }
    }
  }
}
