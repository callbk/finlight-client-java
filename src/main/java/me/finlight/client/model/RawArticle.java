package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An unenriched article as delivered by the raw WebSocket stream — no sentiment, entities, or
 * content, but delivered with minimal latency.
 *
 * @param link canonical URL of the article
 * @param title article headline
 * @param publishDate publication timestamp
 * @param source domain of the publishing source
 * @param language language of the article (ISO 639-1)
 * @param summary article summary, if available
 * @param images image URLs (never null, possibly empty)
 * @param createdAt time the article was first indexed by finlight, if available
 * @param revisedDate time of the latest revision; present when {@code includeUpdates} is enabled
 * @param isUpdate whether this delivery is a revision of a previously delivered article
 * @param categories article categories (never null, possibly empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawArticle(
    String link,
    String title,
    Instant publishDate,
    String source,
    String language,
    @Nullable String summary,
    List<String> images,
    @Nullable Instant createdAt,
    @Nullable Instant revisedDate,
    @Nullable Boolean isUpdate,
    List<String> categories) {

  public RawArticle {
    images = images == null ? List.of() : List.copyOf(images);
    categories = categories == null ? List.of() : List.copyOf(categories);
  }
}
