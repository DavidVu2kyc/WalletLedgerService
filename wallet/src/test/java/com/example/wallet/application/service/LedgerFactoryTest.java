package com.example.wallet.application.service;

import static org.assertj.core.api.Assertions.*;

import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.domain.model.TransactionType;
import com.example.wallet.domain.model.Wallet;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LedgerFactory Unit Tests")
class LedgerFactoryTest {

  @Test
  @DisplayName("createCreditTransaction_withValidInputs_createsCorrectRecord")
  void testCreateCreditTransaction() {
    // Given
    Wallet wallet = createWallet(1L, new BigDecimal("100.00"));
    BigDecimal amount = new BigDecimal("50.00");
    BigDecimal balanceBefore = new BigDecimal("100.00");
    String reference = "credit-ref";
    String description = "test credit";

    var command =
        new com.example.wallet.application.dto.CreditWalletCommand(
            1L, "req-123", amount, reference, description);

    // When
    LedgerTransaction tx = LedgerFactory.credit(wallet, command, balanceBefore);

    // Then
    assertThat(tx).isNotNull();
    assertThat(tx.getType()).isEqualTo(TransactionType.CREDIT);
    assertThat(tx.getAmount()).isEqualByComparingTo(amount);
    assertThat(tx.getBalanceBefore()).isEqualByComparingTo(balanceBefore);
    assertThat(tx.getBalanceAfter())
        .isEqualByComparingTo(balanceBefore.add(amount));
    assertThat(tx.getReference()).isEqualTo(reference);
    assertThat(tx.getDescription()).isEqualTo(description);
    assertThat(tx.getRequestId()).isEqualTo("req-123");
    assertThat(tx.getWallet()).isEqualTo(wallet);
    assertThat(tx.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("createDebitTransaction_withValidInputs_createsCorrectRecord")
  void testCreateDebitTransaction() {
    // Given
    Wallet wallet = createWallet(1L, new BigDecimal("100.00"));
    BigDecimal amount = new BigDecimal("30.00");
    BigDecimal balanceBefore = new BigDecimal("100.00");
    String reference = "debit-ref";
    String description = "test debit";

    var command =
        new com.example.wallet.application.dto.DebitWalletCommand(
            1L, "req-456", amount, reference, description);

    // When
    LedgerTransaction tx = LedgerFactory.debit(wallet, command, balanceBefore);

    // Then
    assertThat(tx).isNotNull();
    assertThat(tx.getType()).isEqualTo(TransactionType.DEBIT);
    assertThat(tx.getAmount()).isEqualByComparingTo(amount);
    assertThat(tx.getBalanceBefore()).isEqualByComparingTo(balanceBefore);
    assertThat(tx.getBalanceAfter())
        .isEqualByComparingTo(balanceBefore.subtract(amount));
    assertThat(tx.getReference()).isEqualTo(reference);
    assertThat(tx.getDescription()).isEqualTo(description);
    assertThat(tx.getRequestId()).isEqualTo("req-456");
    assertThat(tx.getWallet()).isEqualTo(wallet);
    assertThat(tx.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("creditTransaction_withLargeAmount_calculatesBalanceCorrectly")
  void testCreditTransaction_LargeAmount() {
    // Given
    Wallet wallet = createWallet(1L, new BigDecimal("999999.99"));
    BigDecimal amount = new BigDecimal("10000.50");
    BigDecimal balanceBefore = new BigDecimal("999999.99");

    var command =
        new com.example.wallet.application.dto.CreditWalletCommand(
            1L, "req-large", amount, "large-credit", "large amount");

    // When
    LedgerTransaction tx = LedgerFactory.credit(wallet, command, balanceBefore);

    // Then
    assertThat(tx.getBalanceAfter())
        .isEqualByComparingTo(new BigDecimal("1010000.49"));
  }

  @Test
  @DisplayName("debitTransaction_withSmallDecimalAmount_preservesPrecision")
  void testDebitTransaction_SmallDecimal() {
    // Given
    Wallet wallet = createWallet(1L, new BigDecimal("0.05"));
    BigDecimal amount = new BigDecimal("0.01");
    BigDecimal balanceBefore = new BigDecimal("0.05");

    var command =
        new com.example.wallet.application.dto.DebitWalletCommand(
            1L, "req-small", amount, "small-debit", "small amount");

    // When
    LedgerTransaction tx = LedgerFactory.debit(wallet, command, balanceBefore);

    // Then
    assertThat(tx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("0.04"));
    assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  // Helper method
  private Wallet createWallet(Long playerId, BigDecimal balance) {
    Wallet wallet = new Wallet();
    wallet.setWalletId(1L);
    wallet.setPlayerId(playerId);
    wallet.setBalance(balance);
    wallet.setVersion(0L);
    wallet.setCreatedAt(Instant.now());
    wallet.setUpdatedAt(Instant.now());
    return wallet;
  }
}
