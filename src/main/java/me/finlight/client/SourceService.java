package me.finlight.client;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import me.finlight.client.internal.ApiClient;
import me.finlight.client.model.Source;

/** Queries the news sources available through the API. */
public final class SourceService {

  private final ApiClient api;

  SourceService(ApiClient api) {
    this.api = api;
  }

  /**
   * Retrieves all available news sources with their configuration.
   *
   * @throws FinlightApiException for non-2xx responses (after retries)
   */
  public List<Source> getSources() {
    return api.get("/v2/sources", Map.of(), new TypeReference<List<Source>>() {});
  }
}
