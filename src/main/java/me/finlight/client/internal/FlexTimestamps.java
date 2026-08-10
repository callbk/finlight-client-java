package me.finlight.client.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Parses the timestamp formats used by the finlight API: RFC 3339 with or without zone,
 * space-separated, and date-only. Zone-less timestamps are interpreted as UTC, matching the sibling
 * clients.
 */
public final class FlexTimestamps {

  private FlexTimestamps() {}

  /**
   * Parses a finlight timestamp into an {@link Instant}.
   *
   * @throws DateTimeParseException if the value matches none of the known formats
   */
  public static Instant parse(String value) {
    String s = value.trim();
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (DateTimeParseException ignored) {
      // fall through to zone-less formats
    }
    String isoLike = s.indexOf(' ') >= 0 ? s.replaceFirst(" ", "T") : s;
    try {
      return LocalDateTime.parse(isoLike).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
      // fall through to date-only
    }
    try {
      return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (DateTimeParseException ignored) {
      throw new DateTimeParseException("cannot parse timestamp", value, 0);
    }
  }
}
