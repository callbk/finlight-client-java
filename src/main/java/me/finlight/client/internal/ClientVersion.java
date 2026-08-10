package me.finlight.client.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the library version baked into the jar at build time. Sent to the server in the {@code
 * x-client-version} and {@code User-Agent} headers.
 */
public final class ClientVersion {

  private static final String VERSION = load();

  private ClientVersion() {}

  /** Version string of the form {@code java/finlight-client@1.2.3}. */
  public static String get() {
    return VERSION;
  }

  private static String load() {
    String version = "dev";
    try (InputStream in =
        ClientVersion.class.getResourceAsStream("/me/finlight/client/version.properties")) {
      if (in != null) {
        Properties props = new Properties();
        props.load(in);
        version = props.getProperty("version", version);
      }
    } catch (IOException ignored) {
      // keep the "dev" fallback
    }
    return "java/finlight-client@" + version;
  }
}
