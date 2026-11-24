package me.finlight.client;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The API answered with an error: a REST call, a refused WebSocket dial or a terminal {@code
 * connection.error}. Known codes have their own subclass ({@link NotFoundException} and so on);
 * unknown codes and answers without a code stay on this class with {@link ApiErrorKind#OTHER}.
 */
public class FinlightApiException extends FinlightException {
  private static final long serialVersionUID = 1L;

  private final int status;
  private final @Nullable ProblemCode code;
  private final @Nullable String type;
  private final @Nullable String title;
  private final @Nullable String detail;
  private final @Nullable String requestId;
  private final List<FieldIssue> errors;
  private final ApiErrorKind apiKind;
  private final @Nullable String raw;

  FinlightApiException(ApiError e) {
    super(e.message(), null);
    this.status = e.status;
    this.code = e.code;
    this.type = e.type;
    this.title = e.title;
    this.detail = e.detail;
    this.requestId = e.requestId;
    this.errors = List.copyOf(e.errors);
    this.apiKind = e.apiKind;
    this.raw = e.raw;
  }

  /** HTTP status of the answer (the dial status for a refused stream). */
  public int status() {
    return status;
  }

  /** The problem code, or null when the answer carried none. Open: unknown codes are kept. */
  public @Nullable ProblemCode code() {
    return code;
  }

  /** The problem {@code type} URL, or null. */
  public @Nullable String type() {
    return type;
  }

  /** The problem {@code title}, or null. */
  public @Nullable String title() {
    return title;
  }

  /** The problem {@code detail}, or null. */
  public @Nullable String detail() {
    return detail;
  }

  /** The request id. When the server sent none, the {@code X-Request-Id} the SDK sent. */
  public @Nullable String requestId() {
    return requestId;
  }

  /** The rejected fields, possibly empty. */
  public List<FieldIssue> errors() {
    return errors;
  }

  /** The kind of API error. */
  public ApiErrorKind apiKind() {
    return apiKind;
  }

  /** The body as received, or null. */
  public @Nullable String raw() {
    return raw;
  }

  /** The body as received (the JSON text, or the plain text of a non-JSON answer), or null. */
  public @Nullable String rawJson() {
    return raw;
  }

  @Override
  public final FinlightErrorKind kind() {
    return FinlightErrorKind.API;
  }
}
