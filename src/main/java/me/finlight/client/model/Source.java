package me.finlight.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A news source available through the API.
 *
 * @param domain domain of the source (e.g. {@code www.reuters.com})
 * @param isDefaultSource whether the source is part of the default source set
 * @param isContentAvailable whether full article content is available; only present on plans with
 *     content access
 * @param originCountry origin country of the source (ISO 3166-1 alpha-2), if available
 * @param languages languages the source publishes in (ISO 639-1), primary first (never null,
 *     possibly empty)
 * @param isCustomSource present and true only when this is a custom source enabled for your
 *     subscription
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Source(
    String domain,
    boolean isDefaultSource,
    @Nullable Boolean isContentAvailable,
    @Nullable String originCountry,
    List<String> languages,
    @Nullable Boolean isCustomSource) {

  public Source {
    languages = languages == null ? List.of() : List.copyOf(languages);
  }
}
