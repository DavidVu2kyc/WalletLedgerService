package com.example.wallet.domain.exception;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Domain Exception Tests")
class DomainExceptionTest {

  @Test
  @DisplayName("WalletNotFoundException_defaultAndMessageConstructors")
  void walletNotFoundException_defaultAndMessageConstructors() {
    assertThat(new WalletNotFoundException().getMessage()).isEqualTo("Wallet not found");
    assertThat(new WalletNotFoundException("custom msg").getMessage()).isEqualTo("custom msg");
  }

  @Test
  @DisplayName("InsufficientBalanceException_defaultAndMessageConstructors")
  void insufficientBalanceException_defaultAndMessageConstructors() {
    assertThat(new InsufficientBalanceException().getMessage()).isEqualTo("Insufficient balance");
    assertThat(new InsufficientBalanceException("custom msg").getMessage()).isEqualTo("custom msg");
  }

  @Test
  @DisplayName("IdeIdempotencyKeyConflictException_defaultAndMessageConstructors")
  void idempotencyConflictException_defaultAndMessageConstructors() {
    assertThat(new IdeIdempotencyKeyConflictException().getMessage())
        .isEqualTo("Idempotency key conflict");
    assertThat(new IdeIdempotencyKeyConflictException("custom msg").getMessage())
        .isEqualTo("custom msg");

    RuntimeException cause = new RuntimeException("cause");
    IdeIdempotencyKeyConflictException withCause =
        new IdeIdempotencyKeyConflictException("custom msg", cause);
    assertThat(withCause.getMessage()).isEqualTo("custom msg");
    assertThat(withCause.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("DuplicateRequestException_defaultAndMessageConstructors")
  void duplicateRequestException_defaultAndMessageConstructors() {
    assertThat(new DuplicateRequestException().getMessage()).isEqualTo("Duplicate request");
    assertThat(new DuplicateRequestException("custom msg").getMessage()).isEqualTo("custom msg");
  }
}