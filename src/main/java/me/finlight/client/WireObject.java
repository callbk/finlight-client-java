package me.finlight.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/**
 * Base of the wire models. Equality and {@link #toString()} follow the known wire fields; unknown
 * keys a newer server sent do not count.
 */
abstract class WireObject {
  WireObject() {}

  /** The known fields as JSON: absent keys stay absent, null stays null. */
  abstract ObjectNode wire();

  /**
   * Writes the model as JSON with the wire keys. Keys that were absent stay absent and null stays
   * null. Keys this SDK version does not know are not written.
   */
  public String toWire() {
    return Json.write(wire());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return o != null && o.getClass() == getClass() && ((WireObject) o).wire().equals(wire());
  }

  @Override
  public int hashCode() {
    return wire().hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + toWire();
  }
}
