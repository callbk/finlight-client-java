package me.finlight.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Filters of a raw stream ({@code client.rawStream}): articles before enrichment, without {@code
 * include}. Only the keys you set are sent, plus {@code payloadVersion} 3 and {@code clientNonce}.
 */
public final class RawStreamParams {
  private final Map<String, Object> values;

  private RawStreamParams(Map<String, Object> values) {
    this.values = values;
  }

  /** A new builder. */
  public static Builder builder() {
    return new Builder(new LinkedHashMap<>());
  }

  /** A builder with these values. */
  public Builder toBuilder() {
    return new Builder(new LinkedHashMap<>(values));
  }

  /** The subscribe keys the caller set, without {@code payloadVersion} and {@code clientNonce}. */
  ObjectNode toJson() {
    return Params.toJson(values);
  }

  @Override
  public String toString() {
    return "RawStreamParams" + Json.write(toJson());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return o instanceof RawStreamParams p && p.toJson().equals(toJson());
  }

  @Override
  public int hashCode() {
    return Objects.hash(toJson());
  }

  /** Builder of {@link RawStreamParams}. Setting a key to null removes it. */
  public static final class Builder {
    private final Map<String, Object> values;

    private Builder(Map<String, Object> values) {
      this.values = values;
    }

    /** Query in the stream grammar. */
    public Builder query(@Nullable String value) {
      Params.set(values, "query", value);
      return this;
    }

    /** Only these domains. */
    public Builder sources(@Nullable List<String> values) {
      Params.set(this.values, "sources", values == null ? null : Params.strings("sources", values));
      return this;
    }

    /** Only these domains. */
    public Builder sources(String @Nullable ... values) {
      return sources(values == null ? null : Arrays.asList(values));
    }

    /** Leave out these domains. */
    public Builder excludeSources(@Nullable List<String> values) {
      Params.set(
          this.values,
          "excludeSources",
          values == null ? null : Params.strings("excludeSources", values));
      return this;
    }

    /** Leave out these domains. */
    public Builder excludeSources(String @Nullable ... values) {
      return excludeSources(values == null ? null : Arrays.asList(values));
    }

    /** One ISO 639-1 language. Server default {@code en}. */
    public Builder language(@Nullable String value) {
      Params.set(values, "language", value);
      return this;
    }

    /** Also deliver {@code article.updated}. Server default false. */
    public Builder includeUpdates(@Nullable Boolean value) {
      Params.set(values, "includeUpdates", value);
      return this;
    }

    /** Builds the parameters. */
    public RawStreamParams build() {
      return new RawStreamParams(new LinkedHashMap<>(values));
    }
  }
}
