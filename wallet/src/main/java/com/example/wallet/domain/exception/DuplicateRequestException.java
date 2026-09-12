package com.example.wallet.domain.exception;

public class DuplicateRequestException extends RuntimeException {

  public DuplicateRequestException() {
    super("Duplicate request");
  }

  public DuplicateRequestException(String message) {
    super(message);
  }
}
