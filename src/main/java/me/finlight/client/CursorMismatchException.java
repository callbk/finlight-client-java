package me.finlight.client;

/** The cursor belongs to a different sort order or filter set ({@code CURSOR_MISMATCH}). */
public final class CursorMismatchException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  CursorMismatchException(ApiError e) {
    super(e);
  }
}
