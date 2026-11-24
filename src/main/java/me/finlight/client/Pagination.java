package me.finlight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/** Paging data of a search page. There is no total count. */
public final class Pagination extends WireObject {
  private final @Nullable Integer limit;
  private final @Nullable Integer returned;
  private final boolean hasMore;
  private final @Nullable String nextCursor;

  Pagination(
      @Nullable Integer limit,
      @Nullable Integer returned,
      boolean hasMore,
      @Nullable String nextCursor) {
    this.limit = limit;
    this.returned = returned;
    this.hasMore = hasMore;
    this.nextCursor = nextCursor;
  }

  /** The page size the server used. */
  public @Nullable Integer limit() {
    return limit;
  }

  /** Number of articles on this page. */
  public @Nullable Integer returned() {
    return returned;
  }

  /** True when another page exists. */
  public boolean hasMore() {
    return hasMore;
  }

  /** Opaque cursor of the next page, or null at the end. Never parse it. */
  public @Nullable String nextCursor() {
    return nextCursor;
  }

  static Pagination read(JsonNode node, String path) throws Wire.Failure {
    ObjectNode o = Wire.object(node, path);
    Boolean more = Wire.optBool(o, "hasMore", path);
    return new Pagination(
        Wire.optInt(o, "limit", path),
        Wire.optInt(o, "returned", path),
        more != null && more,
        Wire.optString(o, "nextCursor", path));
  }

  @Override
  ObjectNode wire() {
    ObjectNode o = Json.object();
    Wire.put(o, "limit", limit == null ? null : limit.longValue());
    Wire.put(o, "returned", returned == null ? null : returned.longValue());
    o.put("hasMore", hasMore);
    Wire.put(o, "nextCursor", nextCursor);
    return o;
  }
}
