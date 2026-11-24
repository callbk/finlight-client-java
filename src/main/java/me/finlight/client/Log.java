package me.finlight.client;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.function.Supplier;

/**
 * Logging through the platform logger named {@code finlight} (R-LOG-1). The SDK logs details at
 * DEBUG and problems at WARNING; it never writes to stdout or stderr itself and never logs keys,
 * secrets, signatures or bodies.
 */
final class Log {
  static final Logger LOGGER = System.getLogger("finlight");

  private Log() {}

  static void debug(Supplier<String> message) {
    if (LOGGER.isLoggable(Level.DEBUG)) {
      LOGGER.log(Level.DEBUG, message.get());
    }
  }

  static void warn(String message) {
    LOGGER.log(Level.WARNING, message);
  }

  static void warn(String message, Throwable error) {
    LOGGER.log(
        Level.WARNING,
        message + ": " + error.getClass().getSimpleName() + " " + error.getMessage());
  }
}
