package me.finlight.client;

/** The API key is missing or invalid ({@code UNAUTHORIZED}). */
public final class UnauthorizedException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  UnauthorizedException(ApiError e) {
    super(e);
  }
}
