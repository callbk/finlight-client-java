package me.finlight.client;

import java.nio.charset.StandardCharsets;

/** Percent-encoding of query names and values (RFC 3986 unreserved characters stay). */
final class Query {
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private Query() {}

  static String encode(String s) {
    StringBuilder b = new StringBuilder();
    for (byte x : s.getBytes(StandardCharsets.UTF_8)) {
      int c = x & 0xff;
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '.'
          || c == '_'
          || c == '~') {
        b.append((char) c);
      } else {
        b.append('%').append(HEX[c >> 4]).append(HEX[c & 15]);
      }
    }
    return b.toString();
  }
}
