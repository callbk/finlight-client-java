package me.finlight.client;

/** The plan does not include this ({@code FORBIDDEN}). */
public final class ForbiddenException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  ForbiddenException(ApiError e) {
    super(e);
  }
}
