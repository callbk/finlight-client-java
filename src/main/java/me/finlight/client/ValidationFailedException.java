package me.finlight.client;

/** The server rejected the request ({@code VALIDATION_FAILED}). See {@link #errors()}. */
public final class ValidationFailedException extends FinlightApiException {
  private static final long serialVersionUID = 1L;

  ValidationFailedException(ApiError e) {
    super(e);
  }
}
