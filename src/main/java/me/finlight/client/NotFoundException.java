package me.finlight.client;

/** Nothing matched the id or link ({@code NOT_FOUND}). */
public final class NotFoundException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  NotFoundException(ApiError e) {
    super(e);
  }
}
