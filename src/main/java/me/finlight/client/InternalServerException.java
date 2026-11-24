package me.finlight.client;

/** The server failed ({@code INTERNAL_ERROR}). */
public final class InternalServerException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  InternalServerException(ApiError e) {
    super(e);
  }
}
