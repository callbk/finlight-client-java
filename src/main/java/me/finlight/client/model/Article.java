package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An enriched news article as returned by the REST API, the enhanced WebSocket stream, and
 * webhooks.
 *
 * @param link canonical URL of the article
 * @param title article headline
 * @param publishDate publication timestamp
 * @param source domain of the publishing source
 * @param language language of the article (ISO 639-1)
 * @param sentiment sentiment label ({@code positive}, {@code negative}, {@code neutral}), if
 *     available
 * @param confidence sentiment confidence between 0 and 1, if available
 * @param summary article summary, if available
 * @param images image URLs (never null, possibly empty)
 * @param content full article content; only present when requested and available on your plan
 * @param companies tagged companies; only present when entities were requested
 * @param createdAt time the article was first indexed by finlight, if available
 * @param revisedDate time of the latest revision; present when the article has been revised
 * @param isUpdate whether this delivery is a revision of a previously delivered article
 * @param categories article categories (never null, possibly empty)
 * @param countries related countries (ISO 3166-1 alpha-2; never null, possibly empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Article(
    String link,
    String title,
    Instant publishDate,
    String source,
    String language,
    @Nullable String sentiment,
    @Nullable Double confidence,
    @Nullable String summary,
    List<String> images,
    @Nullable String content,
    List<Company> companies,
    @Nullable Instant createdAt,
    @Nullable Instant revisedDate,
    @Nullable Boolean isUpdate,
    List<String> categories,
    List<String> countries) {

  public Article {
    images = images == null ? List.of() : List.copyOf(images);
    companies = companies == null ? List.of() : List.copyOf(companies);
    categories = categories == null ? List.of() : List.copyOf(categories);
    countries = countries == null ? List.of() : List.copyOf(countries);
  }
}
