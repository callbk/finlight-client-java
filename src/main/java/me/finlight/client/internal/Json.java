package me.finlight.client.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** The shared, immutable {@link ObjectMapper} of the client. */
public final class Json {

  private static final ObjectMapper MAPPER = createMapper();

  private Json() {}

  /** The configured mapper. Do not mutate its configuration. */
  public static ObjectMapper mapper() {
    return MAPPER;
  }

  private static ObjectMapper createMapper() {
    SimpleModule flexModule = new SimpleModule("finlight-flex-timestamps");
    flexModule.addDeserializer(Instant.class, new FlexInstantDeserializer());
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(flexModule)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Deserializes {@link Instant} from the timestamp formats used by the finlight API (see {@link
   * FlexTimestamps}).
   */
  static final class FlexInstantDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      String value = parser.getValueAsString();
      try {
        return FlexTimestamps.parse(value);
      } catch (DateTimeParseException e) {
        throw InvalidFormatException.from(
            parser, "Cannot parse timestamp: " + value, value, Instant.class);
      }
    }
  }
}
