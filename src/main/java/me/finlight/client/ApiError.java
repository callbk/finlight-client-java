package me.finlight.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Mutable carrier for the fields of a {@link FinlightApiException} before it is built. */
final class ApiError {
  int status;
  @Nullable ProblemCode code;
  @Nullable String type;
  @Nullable String title;
  @Nullable String detail;
  @Nullable String requestId;
  List<FieldIssue> errors = new ArrayList<>();
  ApiErrorKind apiKind = ApiErrorKind.OTHER;
  @Nullable String raw;
  @Nullable RateLimitType limitType;
  @Nullable Duration retryAfter;
  @Nullable String hint;

  String message() {
    StringBuilder b = new StringBuilder("finlight API error ").append(status);
    if (code != null) {
      b.append(' ').append(code.value());
    }
    String text = detail != null ? detail : title;
    if (text != null) {
      b.append(": ").append(text);
    }
    if (hint != null) {
      b.append(' ').append(hint);
    }
    if (requestId != null) {
      b.append(" (request ").append(requestId).append(')');
    }
    return b.toString();
  }

  /** The kind for a problem code. */
  static ApiErrorKind kindOf(@Nullable ProblemCode code) {
    if (code == null) {
      return ApiErrorKind.OTHER;
    }
    switch (code.value()) {
      case ProblemCodeValues.VALIDATION_FAILED:
        return ApiErrorKind.VALIDATION_FAILED;
      case ProblemCodeValues.CURSOR_INVALID:
        return ApiErrorKind.CURSOR_INVALID;
      case ProblemCodeValues.CURSOR_MISMATCH:
        return ApiErrorKind.CURSOR_MISMATCH;
      case ProblemCodeValues.UNAUTHORIZED:
        return ApiErrorKind.UNAUTHORIZED;
      case ProblemCodeValues.FORBIDDEN:
        return ApiErrorKind.FORBIDDEN;
      case ProblemCodeValues.NOT_FOUND:
        return ApiErrorKind.NOT_FOUND;
      case ProblemCodeValues.RATE_LIMITED:
        return ApiErrorKind.RATE_LIMITED;
      case ProblemCodeValues.INTERNAL_ERROR:
        return ApiErrorKind.INTERNAL_ERROR;
      default:
        return ApiErrorKind.OTHER;
    }
  }

  /** Builds the subclass for {@link #apiKind}. */
  FinlightApiException build() {
    switch (apiKind) {
      case VALIDATION_FAILED:
        return new ValidationFailedException(this);
      case CURSOR_INVALID:
        return new CursorInvalidException(this);
      case CURSOR_MISMATCH:
        return new CursorMismatchException(this);
      case UNAUTHORIZED:
        return new UnauthorizedException(this);
      case FORBIDDEN:
        return new ForbiddenException(this);
      case NOT_FOUND:
        return new NotFoundException(this);
      case RATE_LIMITED:
        return new RateLimitedException(this);
      case INTERNAL_ERROR:
        return new InternalServerException(this);
      default:
        return new FinlightApiException(this);
    }
  }
}
