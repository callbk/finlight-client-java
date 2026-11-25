package me.finlight.client;

/** Maps an API error kind to the class the SDK must throw for it. */
final class ErrorChecks {
  private ErrorChecks() {}

  static Class<? extends FinlightApiException> classFor(ApiErrorKind kind) {
    return switch (kind) {
      case VALIDATION_FAILED -> ValidationFailedException.class;
      case CURSOR_INVALID -> CursorInvalidException.class;
      case CURSOR_MISMATCH -> CursorMismatchException.class;
      case UNAUTHORIZED -> UnauthorizedException.class;
      case FORBIDDEN -> ForbiddenException.class;
      case NOT_FOUND -> NotFoundException.class;
      case RATE_LIMITED -> RateLimitedException.class;
      case INTERNAL_ERROR -> InternalServerException.class;
      case OTHER -> FinlightApiException.class;
    };
  }
}
