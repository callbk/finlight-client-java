package me.finlight.client;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the presence state of a key path ({@code companies[0].primaryListing.ticker}) through the
 * public accessors of a decoded model: absent, null, empty or value.
 */
final class Presence {
  private static final Pattern SEGMENT = Pattern.compile("(\\w+)(?:\\[(\\d+)])?");

  private Presence() {}

  static String of(Object root, String path) {
    Object cur = root;
    for (String part : path.split("\\.")) {
      Matcher m = SEGMENT.matcher(part);
      if (!m.matches()) {
        throw new IllegalArgumentException(part);
      }
      Object v;
      try {
        Method method = cur.getClass().getMethod(m.group(1));
        v = method.invoke(cur);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(
            "no accessor " + m.group(1) + " on " + cur.getClass().getSimpleName(), e);
      }
      if (v instanceof Gated<?> g) {
        if (!g.isPresent()) {
          return "absent";
        }
        v = g.orNull();
      }
      if (m.group(2) != null) {
        if (v == null) {
          return "null";
        }
        v = ((List<?>) v).get(Integer.parseInt(m.group(2)));
      }
      if (v == null) {
        return "null";
      }
      cur = v;
    }
    if (cur instanceof List<?> l && l.isEmpty()) {
      return "empty";
    }
    return "value";
  }
}
