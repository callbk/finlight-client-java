package me.finlight.client;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Strict RFC 3339 parsing and fixed, locale-independent formatting (R-MODELS-2, R-REQ-2). */
final class Times {
  private static final Pattern RFC3339 =
      Pattern.compile(
          "\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?([Zz]|[+-]\\d{2}:\\d{2})");

  private static final DateTimeFormatter PARSER =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME
          .withResolverStyle(ResolverStyle.STRICT)
          .withChronology(IsoChronology.INSTANCE)
          .withLocale(Locale.ROOT);

  // Instant.toString() drops zero milliseconds; the wire format always has three digits.
  private static final DateTimeFormatter OUT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
          .withChronology(IsoChronology.INSTANCE)
          .withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withChronology(IsoChronology.INSTANCE);

  private Times() {}

  /** Parses an RFC 3339 time with {@code Z} or an offset; null for anything else. */
  static @Nullable Instant parse(@Nullable String text) {
    if (text == null || !RFC3339.matcher(text).matches()) {
      return null;
    }
    try {
      String normalized = text.replace('t', 'T').replace('z', 'Z');
      return OffsetDateTime.parse(normalized, PARSER).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** UTC ISO 8601 with milliseconds and {@code Z}. */
  static String format(Instant instant) {
    return OUT.format(instant);
  }

  /** {@code YYYY-MM-DD}. */
  static String format(LocalDate date) {
    return DATE.format(date);
  }
}
