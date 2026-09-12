package com.example.wallet.domain;

import static org.assertj.core.api.Assertions.*;

import com.example.wallet.domain.exception.InsufficientBalanceException;
import com.example.wallet.domain.model.Wallet;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Wallet Domain Unit Tests")
class WalletTest {

  @Test
  @DisplayName("credit_whenValidAmount_increasesBalance")
  void credit_whenValidAmount_increasesBalance() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When
    wallet.credit(new BigDecimal("50.00"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  @DisplayName("credit_whenAmountIsZero_throwsIllegalArgumentException")
  void credit_whenAmountIsZero_throwsIllegalArgumentException() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When & Then
    assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Amount must be positive");
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("credit_whenAmountIsNegative_throwsIllegalArgumentException")
  void credit_whenAmountIsNegative_throwsIllegalArgumentException() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When & Then
    assertThatThrownBy(() -> wallet.credit(new BigDecimal("-10.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Amount must be positive");
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("credit_whenAmountIsNull_throwsIllegalArgumentException")
  void credit_whenAmountIsNull_throwsIllegalArgumentException() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When & Then
    assertThatThrownBy(() -> wallet.credit(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Amount must be positive");
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("debit_whenBalanceIsSufficient_decreasesBalance")
  void debit_whenBalanceIsSufficient_decreasesBalance() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When
    wallet.debit(new BigDecimal("30.00"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
  }

  @Test
  @DisplayName("debit_whenBalanceIsExactlyEqual_succeedsAndBalanceIsZero")
  void debit_whenBalanceIsExactlyEqual_succeedsAndBalanceIsZero() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When
    wallet.debit(new BigDecimal("100.00"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("debit_whenBalanceIsInsufficient_throwsInsufficientBalanceException")
  void debit_whenBalanceIsInsufficient_throwsInsufficientBalanceException() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When & Then
    assertThatThrownBy(() -> wallet.debit(new BigDecimal("100.01")))
        .isInstanceOf(InsufficientBalanceException.class);
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("debit_whenAmountIsZero_throwsIllegalArgumentException")
  void debit_whenAmountIsZero_throwsIllegalArgumentException() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When & Then
    assertThatThrownBy(() -> wallet.debit(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Amount must be positive");
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("debit_whenAmountIsNegative_throwsIllegalArgumentException")
  void debit_whenAmountIsNegative_throwsIllegalArgumentException() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("100.00"));

    // When & Then
    assertThatThrownBy(() -> wallet.debit(new BigDecimal("-5.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Amount must be positive");
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("credit_withDecimalAmount_preservesMonetaryPrecision")
  void credit_withDecimalAmount_preservesMonetaryPrecision() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("0.00"));

    // When
    wallet.credit(new BigDecimal("0.01"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  @DisplayName("debit_withDecimalAmount_preservesMonetaryPrecision")
  void debit_withDecimalAmount_preservesMonetaryPrecision() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("0.05"));

    // When
    wallet.debit(new BigDecimal("0.01"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.04"));
  }

  @Test
  @DisplayName("credit_withVeryLargeAmount_handlesLargeValues")
  void credit_withVeryLargeAmount_handlesLargeValues() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("999999999.99"));

    // When
    wallet.credit(new BigDecimal("1000000.01"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("1001000000.00"));
  }

  @Test
  @DisplayName("credit_withMinimumValidAmount_acceptsOneCent")
  void credit_withMinimumValidAmount_acceptsOneCent() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("0.00"));

    // When
    wallet.credit(new BigDecimal("0.01"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  @DisplayName("multipleCreditsAndDebits_accumulateCorrectly")
  void multipleCreditsAndDebits_accumulateCorrectly() {
    // Given
    Wallet wallet = createWallet(new BigDecimal("0.00"));

    // When
    wallet.credit(new BigDecimal("100.00"));
    wallet.debit(new BigDecimal("30.00"));
    wallet.credit(new BigDecimal("50.00"));
    wallet.debit(new BigDecimal("20.00"));

    // Then
    assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  private Wallet createWallet(BigDecimal balance) {
    Wallet wallet = new Wallet();
    wallet.setWalletId(1L);
    wallet.setPlayerId(1L);
    wallet.setBalance(balance);
    wallet.setVersion(0L);
    wallet.setCreatedAt(Instant.now());
    wallet.setUpdatedAt(Instant.now());
    return wallet;
  }
}