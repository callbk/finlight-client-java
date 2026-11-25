package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An article, as delivered by REST, streams and webhooks.
 *
 * <p>Timestamps are kept as the strings that were sent ({@link #publishedAt()}); the {@code
 * *Time()} methods parse them strictly and return null for anything unreadable. Keys that depend on
 * the plan or on {@code include} are {@link Gated}: check {@link #contentPresent()} and the other
 * {@code *Present()} methods.
 */
public final class Article extends WireObject {
  private final String id;
  private final String link;
  private final ArticleSource source;
  private final String title;
  private final @Nullable String summary;
  private final @Nullable String language;
  private final List<Media> media;
  private final @Nullable String publishedAt;
  private final @Nullable String firstSeenAt;
  private final @Nullable String revisedAt;
  private final @Nullable String availableAt;
  private final List<String> countries;
  private final List<String> categories;
  private final Gated<String> content;
  private final Gated<Sentiment> sentiment;
  private final Gated<List<Company>> companies;
  private final Gated<List<EventTag>> events;
  private final String rawJson;

  private Article(ObjectNode o, String path) throws Wire.Failure {
    this.id = Wire.reqString(o, "id", path);
    this.link = Wire.reqString(o, "link", path);
    this.source = ArticleSource.read(o.get("source"), Wire.at(path, "source"));
    this.title = Wire.reqString(o, "title", path);
    this.summary = Wire.optString(o, "summary", path);
    this.language = Wire.optString(o, "language", path);
    this.media = Wire.list(o, "media", path, Media::read);
    this.publishedAt = Wire.optString(o, "publishedAt", path);
    this.firstSeenAt = Wire.optString(o, "firstSeenAt", path);
    this.revisedAt = Wire.optString(o, "revisedAt", path);
    this.availableAt = Wire.optString(o, "availableAt", path);
    this.countries = Wire.list(o, "countries", path, Wire::string);
    this.categories = Wire.list(o, "categories", path, Wire::string);
    this.content = o.has("content") ? Gated.of(Wire.optString(o, "content", path)) : Gated.absent();
    this.sentiment =
        !o.has("sentiment")
            ? Gated.absent()
            : Gated.of(
                Wire.absentOrNull(o, "sentiment")
                    ? null
                    : Sentiment.read(o.get("sentiment"), Wire.at(path, "sentiment")));
    this.companies = Wire.gatedList(o, "companies", path, Company::read);
    this.events = Wire.gatedList(o, "events", path, EventTag::read);
    this.rawJson = Json.write(o);
  }

  static Article read(JsonNode node, String path) throws Wire.Failure {
    return new Article(Wire.object(node, path), path);
  }

  /** Stable id, the same in every channel. Use it with {@code articles().get(id)}. */
  public String id() {
    return id;
  }

  /** The article URL. */
  public String link() {
    return link;
  }

  /** The source. */
  public ArticleSource source() {
    return source;
  }

  /** The title in the original language. */
  public String title() {
    return title;
  }

  /** The summary, or null (never an empty string). */
  public @Nullable String summary() {
    return summary;
  }

  /** ISO 639-1 language, or null. */
  public @Nullable String language() {
    return language;
  }

  /** Images, at most 3; empty when there are none. */
  public List<Media> media() {
    return media;
  }

  /** Publication time claimed by the source, as sent. Old articles can carry 1970-01-01. */
  public @Nullable String publishedAt() {
    return publishedAt;
  }

  /** When finlight first saw the article, as sent. */
  public @Nullable String firstSeenAt() {
    return firstSeenAt;
  }

  /** The source's revision time, as sent, or null. */
  public @Nullable String revisedAt() {
    return revisedAt;
  }

  /** When the enriched article became available, as sent. Always null on raw streams. */
  public @Nullable String availableAt() {
    return availableAt;
  }

  /** ISO 3166-1 alpha-2 countries the story is about; possibly empty. */
  public List<String> countries() {
    return countries;
  }

  /** Categories (open vocabulary); possibly empty. */
  public List<String> categories() {
    return categories;
  }

  /**
   * Full text: absent unless requested with {@code include} and entitled; null when there is none.
   */
  public Gated<String> content() {
    return content;
  }

  /** Sentiment: absent without the entitlement (and on raw); null when not computed. */
  public Gated<Sentiment> sentiment() {
    return sentiment;
  }

  /** Tagged companies: absent unless requested and entitled; empty when tagged and none found. */
  public Gated<List<Company>> companies() {
    return companies;
  }

  /** Tagged events: absent unless requested and entitled; empty when classified and none found. */
  public Gated<List<EventTag>> events() {
    return events;
  }

  /** True when the {@code content} key was sent, also when it was null. */
  public boolean contentPresent() {
    return content.isPresent();
  }

  /** True when the {@code sentiment} key was sent, also when it was null. */
  public boolean sentimentPresent() {
    return sentiment.isPresent();
  }

  /** True when the {@code companies} key was sent. */
  public boolean companiesPresent() {
    return companies.isPresent();
  }

  /** True when the {@code events} key was sent. */
  public boolean eventsPresent() {
    return events.isPresent();
  }

  /** {@link #publishedAt()} parsed strictly (RFC 3339), or null. */
  public @Nullable Instant publishedAtTime() {
    return Times.parse(publishedAt);
  }

  /** {@link #firstSeenAt()} parsed strictly (RFC 3339), or null. */
  public @Nullable Instant firstSeenAtTime() {
    return Times.parse(firstSeenAt);
  }

  /** {@link #revisedAt()} parsed strictly (RFC 3339), or null. */
  public @Nullable Instant revisedAtTime() {
    return Times.parse(revisedAt);
  }

  /** {@link #availableAt()} parsed strictly (RFC 3339), or null. */
  public @Nullable Instant availableAtTime() {
    return Times.parse(availableAt);
  }

  /** The article JSON as received, including keys this SDK version does not know. */
  public String rawJson() {
    return rawJson;
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    o.put("id", id);
    o.put("link", link);
    o.set("source", source.wire());
    o.put("title", title);
    Wire.put(o, "summary", summary);
    Wire.put(o, "language", language);
    ArrayNode m = o.putArray("media");
    media.forEach(x -> m.add(x.wire()));
    Wire.put(o, "publishedAt", publishedAt);
    Wire.put(o, "firstSeenAt", firstSeenAt);
    Wire.put(o, "revisedAt", revisedAt);
    Wire.put(o, "availableAt", availableAt);
    o.set("countries", Wire.strings(countries));
    o.set("categories", Wire.strings(categories));
    if (content.isPresent()) {
      Wire.put(o, "content", content.orNull());
    }
    if (sentiment.isPresent()) {
      Sentiment s = sentiment.orNull();
      if (s == null) {
        o.putNull("sentiment");
      } else {
        o.set("sentiment", s.wire());
      }
    }
    List<Company> cs = companies.orNull();
    if (cs != null) {
      ArrayNode a = o.putArray("companies");
      cs.forEach(c -> a.add(c.wire()));
    }
    List<EventTag> es = events.orNull();
    if (es != null) {
      ArrayNode a = o.putArray("events");
      es.forEach(e -> a.add(e.wire()));
    }
    return o;
  }
}
