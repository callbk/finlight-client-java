package me.finlight.client;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Options of {@code articles().get(id, options)} and {@code getByLink(link, options)}. */
public final class LookupOptions {
  private static final LookupOptions NONE = new LookupOptions(null);

  private final @Nullable ArrayNode include;

  private LookupOptions(@Nullable ArrayNode include) {
    this.include = include;
  }

  /** No options. */
  public static LookupOptions none() {
    return NONE;
  }

  /** Blocks to add: {@code content}, {@code companies}, {@code events}; at most 3. */
  public static LookupOptions include(List<String> blocks) {
    return new LookupOptions(Params.include("include", blocks));
  }

  /** Blocks to add: {@code content}, {@code companies}, {@code events}; at most 3. */
  public static LookupOptions include(String... blocks) {
    return include(Arrays.asList(blocks));
  }

  /** Blocks to add; at most 3. */
  public static LookupOptions include(IncludeBlock... blocks) {
    List<String> out = new ArrayList<>();
    for (IncludeBlock b : blocks) {
      if (b == null) {
        throw new FinlightValidationException("include", "include must not contain null");
      }
      out.add(b.value());
    }
    return include(out);
  }

  /** The include values as one comma-joined query value, or null. */
  @Nullable String includeQuery() {
    if (include == null) {
      return null;
    }
    List<String> parts = new ArrayList<>();
    include.forEach(n -> parts.add(n.asText()));
    return String.join(",", parts);
  }

  @Override
  public String toString() {
    return "LookupOptions{include=" + includeQuery() + "}";
  }
}
