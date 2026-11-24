package me.finlight.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Filters of an enriched stream ({@code client.stream}). Only the keys you set are sent, plus the
 * SDK's {@code payloadVersion} (always the number 3) and {@code clientNonce}.
 */
public final class StreamParams {
  private final Map<String, Object> values;

  private StreamParams(Map<String, Object> values) {
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
    return "StreamParams" + Json.write(toJson());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return o instanceof StreamParams p && p.toJson().equals(toJson());
  }

  @Override
  public int hashCode() {
    return Objects.hash(toJson());
  }

  /** Builder of {@link StreamParams}. Setting a key to null removes it. */
  public static final class Builder {
    private final Map<String, Object> values;

    private Builder(Map<String, Object> values) {
      this.values = values;
    }

    /** Query in the stream grammar (lenient: unknown fields fall back to full text). */
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

    /** Add non-default sources. */
    public Builder optInSources(@Nullable List<String> values) {
      Params.set(
          this.values,
          "optInSources",
          values == null ? null : Params.strings("optInSources", values));
      return this;
    }

    /** Add non-default sources. */
    public Builder optInSources(String @Nullable ... values) {
      return optInSources(values == null ? null : Arrays.asList(values));
    }

    /** One ISO 639-1 language. Server default {@code en}. */
    public Builder language(@Nullable String value) {
      Params.set(values, "language", value);
      return this;
    }

    /** Tickers. */
    public Builder tickers(@Nullable List<String> values) {
      Params.set(this.values, "tickers", values == null ? null : Params.strings("tickers", values));
      return this;
    }

    /** Tickers. */
    public Builder tickers(String @Nullable ... values) {
      return tickers(values == null ? null : Arrays.asList(values));
    }

    /** Countries the story is about (ISO alpha-2). */
    public Builder countries(@Nullable List<String> values) {
      Params.set(
          this.values, "countries", values == null ? null : Params.strings("countries", values));
      return this;
    }

    /** Countries the story is about (ISO alpha-2). */
    public Builder countries(String @Nullable ... values) {
      return countries(values == null ? null : Arrays.asList(values));
    }

    /** Categories. */
    public Builder categories(@Nullable List<String> values) {
      Params.set(
          this.values, "categories", values == null ? null : Params.strings("categories", values));
      return this;
    }

    /** Categories. */
    public Builder categories(String @Nullable ... values) {
      return categories(values == null ? null : Arrays.asList(values));
    }

    /** Leave out articles without content. */
    public Builder excludeEmptyContent(@Nullable Boolean value) {
      Params.set(values, "excludeEmptyContent", value);
      return this;
    }

    /** Also deliver {@code article.updated}. Server default false. */
    public Builder includeUpdates(@Nullable Boolean value) {
      Params.set(values, "includeUpdates", value);
      return this;
    }

    /** Blocks to add: {@code content}, {@code companies}, {@code events}; at most 3. */
    public Builder include(@Nullable List<String> blocks) {
      Params.set(values, "include", blocks == null ? null : Params.include("include", blocks));
      return this;
    }

    /** Blocks to add; at most 3. */
    public Builder include(String @Nullable ... blocks) {
      return include(blocks == null ? null : Arrays.asList(blocks));
    }

    /** Blocks to add; at most 3. */
    public Builder include(IncludeBlock... blocks) {
      List<String> out = new ArrayList<>();
      for (IncludeBlock b : blocks) {
        out.add(Objects.requireNonNull(b, "include").value());
      }
      return include(out);
    }

    /** Builds the parameters. */
    public StreamParams build() {
      return new StreamParams(new LinkedHashMap<>(values));
    }
  }
}
