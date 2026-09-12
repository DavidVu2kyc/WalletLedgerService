package com.example.wallet.domain.exception;

public class IdeIdempotencyKeyConflictException extends RuntimeException {

  public IdeIdempotencyKeyConflictException() {
    super("Idempotency key conflict");
  }

  public IdeIdempotencyKeyConflictException(String message) {
    super(message);
  }

  public IdeIdempotencyKeyConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
