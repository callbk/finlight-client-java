package me.finlight.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One entry of {@link FinlightApiException#errors()}: a request field the server rejected. */
public final class FieldIssue {
  private final @Nullable String field;
  private final @Nullable String issue;
  private final List<String> allowedValues;
  private final Map<String, String> constraints;

  FieldIssue(
      @Nullable String field,
      @Nullable String issue,
      List<String> allowedValues,
      Map<String, String> constraints) {
    this.field = field;
    this.issue = issue;
    this.allowedValues = List.copyOf(allowedValues);
    this.constraints = Map.copyOf(constraints);
  }

  /** The request field, for example {@code eventTypes}, or null. */
  public @Nullable String field() {
    return field;
  }

  /** What is wrong with it, or null. */
  public @Nullable String issue() {
    return issue;
  }

  /** The values the server accepts, for closed request vocabularies; else empty. */
  public List<String> allowedValues() {
    return allowedValues;
  }

  /** Rule name to text, only on stream errors ({@code details[].constraints}); else empty. */
  public Map<String, String> constraints() {
    return constraints;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return o instanceof FieldIssue f
        && Objects.equals(field, f.field)
        && Objects.equals(issue, f.issue)
        && allowedValues.equals(f.allowedValues)
        && constraints.equals(f.constraints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, issue, allowedValues, constraints);
  }

  @Override
  public String toString() {
    return "FieldIssue{field=" + field + ", issue=" + issue + "}";
  }
}
