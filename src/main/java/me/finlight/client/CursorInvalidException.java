package me.finlight.client;

/** The cursor could not be read ({@code CURSOR_INVALID}). Start a new search. */
public final class CursorInvalidException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  CursorInvalidException(ApiError e) {
    super(e);
  }
}
