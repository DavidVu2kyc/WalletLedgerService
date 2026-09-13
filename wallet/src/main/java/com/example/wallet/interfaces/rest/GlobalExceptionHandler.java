package com.example.wallet.interfaces.rest;

import com.example.wallet.domain.exception.DuplicateRequestException;
import com.example.wallet.domain.exception.IdeIdempotencyKeyConflictException;
import com.example.wallet.domain.exception.InsufficientBalanceException;
import com.example.wallet.domain.exception.WalletNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(WalletNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleWalletNotFoundException(
      WalletNotFoundException ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse("WALLET_NOT_FOUND", ex.getMessage(), Instant.now(), new HashMap<>());
    return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(InsufficientBalanceException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientBalanceException(
      InsufficientBalanceException ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse("INSUFFICIENT_BALANCE", ex.getMessage(), Instant.now(), new HashMap<>());
    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(IdeIdempotencyKeyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyKeyConflictException(
      IdeIdempotencyKeyConflictException ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse(
            "IDEMPOTENCY_KEY_CONFLICT", ex.getMessage(), Instant.now(), new HashMap<>());
    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(DuplicateRequestException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateRequestException(
      DuplicateRequestException ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse("DUPLICATE_REQUEST", ex.getMessage(), Instant.now(), new HashMap<>());
    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex, WebRequest request) {
    Map<String, Object> details = new HashMap<>();
    Map<String, String> fieldErrors = new HashMap<>();

    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

    details.put("fieldErrors", fieldErrors);

    ErrorResponse error =
        new ErrorResponse("VALIDATION_ERROR", "Validation failed", Instant.now(), details);
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex, WebRequest request) {
    Map<String, String> fieldErrors = new HashMap<>();

    ex.getAllErrors()
        .forEach(
            error -> {
              String code =
                  error.getCodes() != null && error.getCodes().length > 0
                      ? error.getCodes()[0]
                      : "";
              String field = code.contains(".") ? code.substring(code.lastIndexOf('.') + 1) : code;
              String message =
                  error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
              fieldErrors.putIfAbsent(field, message);
            });

    Map<String, Object> details = new HashMap<>();
    details.put("fieldErrors", fieldErrors);

    ErrorResponse error =
        new ErrorResponse("VALIDATION_ERROR", "Validation failed", Instant.now(), details);
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
      MissingRequestHeaderException ex, WebRequest request) {
    Map<String, Object> details = new HashMap<>();
    Map<String, String> headerErrors = new HashMap<>();
    headerErrors.put(
        ex.getHeaderName(), "Header '" + ex.getHeaderName() + "' is required");
    details.put("headerErrors", headerErrors);

    ErrorResponse error =
        new ErrorResponse("VALIDATION_ERROR", "Validation failed", Instant.now(), details);
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
      IllegalArgumentException ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse("VALIDATION_ERROR", ex.getMessage(), Instant.now(), new HashMap<>());
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse(
            "INTERNAL_ERROR", "An unexpected error occurred", Instant.now(), new HashMap<>());
    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
