package com.example.wallet.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.wallet.application.dto.BalanceResponse;
import com.example.wallet.application.dto.WalletOperationRequest;
import com.example.wallet.application.dto.WalletOperationResponse;
import com.example.wallet.domain.exception.IdeIdempotencyKeyConflictException;
import com.example.wallet.domain.exception.InsufficientBalanceException;
import com.example.wallet.domain.exception.WalletNotFoundException;
import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.domain.model.TransactionType;
import com.example.wallet.domain.model.Wallet;
import com.example.wallet.infrastructure.persistence.repository.LedgerTransactionRepository;
import com.example.wallet.infrastructure.persistence.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests")
class WalletServiceTest {

  @Mock private WalletRepository walletRepository;

  @Mock private LedgerTransactionRepository ledgerTransactionRepository;

  private WalletService walletService;

  @BeforeEach
  void setUp() {
    walletService = new WalletService(walletRepository, ledgerTransactionRepository);
  }

  @Test
  @DisplayName("creditWallet_whenValidAmount_creditsSuccessfully")
  void creditWallet_whenValidAmount_creditsSuccessfully() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "credit-key-123";
    BigDecimal creditAmount = new BigDecimal("50.00");
    BigDecimal initialBalance = new BigDecimal("100.00");

    Wallet wallet = createWallet(playerId, initialBalance);
    WalletOperationRequest request =
        new WalletOperationRequest(creditAmount, "test-ref", "test credit");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
    when(ledgerTransactionRepository.save(any(LedgerTransaction.class)))
        .thenAnswer(
            invocation -> {
              LedgerTransaction tx = invocation.getArgument(0);
              tx.setTransactionId(1L);
              return tx;
            });

    // When
    WalletOperationResponse response = walletService.credit(playerId, idempotencyKey, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.playerId()).isEqualTo(playerId);
    assertThat(response.amount()).isEqualByComparingTo(creditAmount);
    assertThat(response.balanceBefore()).isEqualByComparingTo(initialBalance);
    assertThat(response.balanceAfter()).isEqualByComparingTo(initialBalance.add(creditAmount));
    assertThat(response.type()).isEqualTo("CREDIT");
    assertThat(response.reference()).isEqualTo("test-ref");
    assertThat(response.transactionId()).isEqualTo(1L);
    assertThat(response.createdAt()).isNotNull();
    verify(walletRepository).findByPlayerIdForUpdate(playerId);
    verify(walletRepository).save(any(Wallet.class));
    verify(ledgerTransactionRepository).save(any(LedgerTransaction.class));
  }

  @Test
  @DisplayName("creditWallet_whenWalletNotFound_throwsWalletNotFoundException")
  void creditWallet_whenWalletNotFound_throwsWalletNotFoundException() {
    // Given
    Long playerId = 999L;
    String idempotencyKey = "credit-key-456";
    BigDecimal creditAmount = new BigDecimal("50.00");
    WalletOperationRequest request =
        new WalletOperationRequest(creditAmount, "test-ref", "test credit");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> walletService.credit(playerId, idempotencyKey, request))
        .isInstanceOf(WalletNotFoundException.class)
        .hasMessageContaining("Wallet not found for playerId: 999");
    verify(walletRepository, never()).save(any());
    verify(ledgerTransactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("creditWallet_whenSameIdempotencyKeySubmittedTwice_returnsOriginalResponse")
  void creditWallet_whenSameIdempotencyKeySubmittedTwice_returnsOriginalResponse() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "credit-key-idempotent";
    BigDecimal creditAmount = new BigDecimal("50.00");

    Wallet wallet = createWallet(playerId, new BigDecimal("100.00"));
    WalletOperationRequest request =
        new WalletOperationRequest(creditAmount, "test-ref", "test credit");

    LedgerTransaction existingTx =
        createLedgerTransaction(
            1L, wallet, TransactionType.CREDIT, creditAmount, idempotencyKey);

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.of(existingTx));

    // When
    WalletOperationResponse response = walletService.credit(playerId, idempotencyKey, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.transactionId()).isEqualTo(1L);
    assertThat(response.amount()).isEqualByComparingTo(creditAmount);
    verify(walletRepository, never()).findByPlayerIdForUpdate(anyLong());
    verify(walletRepository, never()).save(any());
    verify(ledgerTransactionRepository, never()).save(any());
  }

  @Test
@DisplayName("creditWallet_whenIdempotencyKeyReusedWithDifferentPayload_returnsConflict")
void creditWallet_whenIdempotencyKeyReusedWithDifferentPayload_returnsConflict() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "credit-key-conflict";
    BigDecimal creditAmount = new BigDecimal("50.00");

    Wallet wallet = createWallet(playerId, new BigDecimal("100.00"));
    WalletOperationRequest request =
        new WalletOperationRequest(creditAmount, "test-ref", "test credit");

    // Existing tx with DIFFERENT amount → conflict
    LedgerTransaction conflictingTx =
        createLedgerTransaction(
            2L, wallet, TransactionType.CREDIT, new BigDecimal("99.00"), idempotencyKey);
    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.of(conflictingTx));

    // When & Then
    assertThatThrownBy(() -> walletService.credit(playerId, idempotencyKey, request))
        .isInstanceOf(IdeIdempotencyKeyConflictException.class)
        .hasMessageContaining("Idempotency key already used with different parameters");
}

@Test
@DisplayName("creditWallet_whenIdempotencyKeyConflictButSamePayload_returnsOriginalResponse")
void creditWallet_whenIdempotencyKeyConflictButSamePayload_returnsOriginalResponse() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "credit-key-same-payload";
    BigDecimal creditAmount = new BigDecimal("50.00");

    Wallet wallet = createWallet(playerId, new BigDecimal("100.00"));
    WalletOperationRequest request =
        new WalletOperationRequest(creditAmount, "test-ref", "test credit");

    // Existing tx with SAME amount, type, reference → idempotent replay
    LedgerTransaction existingTx =
        createLedgerTransaction(3L, wallet, TransactionType.CREDIT, creditAmount, idempotencyKey);
    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.of(existingTx));

    // When
    WalletOperationResponse response = walletService.credit(playerId, idempotencyKey, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.transactionId()).isEqualTo(3L);
    assertThat(response.amount()).isEqualByComparingTo(creditAmount);
    verify(walletRepository, never()).findByPlayerIdForUpdate(anyLong());
    verify(walletRepository, never()).save(any());
    verify(ledgerTransactionRepository, never()).save(any());
}  


  @Test
  @DisplayName("debitWallet_whenValidAmountAndSufficientBalance_debitsSuccessfully")
  void debitWallet_whenValidAmountAndSufficientBalance_debitsSuccessfully() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "debit-key-789";
    BigDecimal debitAmount = new BigDecimal("30.00");
    BigDecimal initialBalance = new BigDecimal("100.00");

    Wallet wallet = createWallet(playerId, initialBalance);
    WalletOperationRequest request =
        new WalletOperationRequest(debitAmount, "test-ref", "test debit");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
    when(ledgerTransactionRepository.save(any(LedgerTransaction.class)))
        .thenAnswer(
            invocation -> {
              LedgerTransaction tx = invocation.getArgument(0);
              tx.setTransactionId(2L);
              return tx;
            });

    // When
    WalletOperationResponse response = walletService.debit(playerId, idempotencyKey, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.playerId()).isEqualTo(playerId);
    assertThat(response.amount()).isEqualByComparingTo(debitAmount);
    assertThat(response.balanceBefore()).isEqualByComparingTo(initialBalance);
    assertThat(response.balanceAfter()).isEqualByComparingTo(initialBalance.subtract(debitAmount));
    assertThat(response.type()).isEqualTo("DEBIT");
    verify(walletRepository).findByPlayerIdForUpdate(playerId);
    verify(walletRepository).save(any(Wallet.class));
    verify(ledgerTransactionRepository).save(any(LedgerTransaction.class));
  }

  @Test
  @DisplayName("debitWallet_whenInsufficientBalance_throwsInsufficientBalanceException")
  void debitWallet_whenInsufficientBalance_throwsInsufficientBalanceException() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "debit-key-insufficient";
    BigDecimal debitAmount = new BigDecimal("150.00");
    BigDecimal initialBalance = new BigDecimal("100.00");

    Wallet wallet = createWallet(playerId, initialBalance);
    WalletOperationRequest request =
        new WalletOperationRequest(debitAmount, "test-ref", "test debit");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.of(wallet));

    // When & Then
    assertThatThrownBy(() -> walletService.debit(playerId, idempotencyKey, request))
        .isInstanceOf(InsufficientBalanceException.class)
        .hasMessageContaining("insufficient");
    verify(walletRepository, never()).save(any());
    verify(ledgerTransactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("debitWallet_whenBalanceExactlyEqualToDebitAmount_succeeds")
  void debitWallet_whenBalanceExactlyEqualToDebitAmount_succeeds() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "debit-key-exact";
    BigDecimal debitAmount = new BigDecimal("100.00");
    BigDecimal initialBalance = new BigDecimal("100.00");

    Wallet wallet = createWallet(playerId, initialBalance);
    WalletOperationRequest request =
        new WalletOperationRequest(debitAmount, "test-ref", "exact debit");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
    when(ledgerTransactionRepository.save(any(LedgerTransaction.class)))
        .thenAnswer(
            invocation -> {
              LedgerTransaction tx = invocation.getArgument(0);
              tx.setTransactionId(3L);
              return tx;
            });

    // When
    WalletOperationResponse response = walletService.debit(playerId, idempotencyKey, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.balanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
    verify(walletRepository).save(any(Wallet.class));
    verify(ledgerTransactionRepository).save(any(LedgerTransaction.class));
  }

  @Test
  @DisplayName("debitWallet_whenBalanceLessThanDebitByOneCent_throwsInsufficientBalance")
  void debitWallet_whenBalanceLessThanDebitByOneCent_throwsInsufficientBalance() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "debit-key-one-cent";
    BigDecimal debitAmount = new BigDecimal("100.01");
    BigDecimal initialBalance = new BigDecimal("100.00");

    Wallet wallet = createWallet(playerId, initialBalance);
    WalletOperationRequest request =
        new WalletOperationRequest(debitAmount, "test-ref", "one cent over");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.of(wallet));

    // When & Then
    assertThatThrownBy(() -> walletService.debit(playerId, idempotencyKey, request))
        .isInstanceOf(InsufficientBalanceException.class);
    verify(walletRepository, never()).save(any());
    verify(ledgerTransactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("debitWallet_whenSameIdempotencyKeySubmittedTwice_returnsOriginalResponse")
  void debitWallet_whenSameIdempotencyKeySubmittedTwice_returnsOriginalResponse() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "debit-key-idempotent";
    BigDecimal debitAmount = new BigDecimal("30.00");

    Wallet wallet = createWallet(playerId, new BigDecimal("100.00"));
    WalletOperationRequest request =
        new WalletOperationRequest(debitAmount, "test-ref", "test debit");

    LedgerTransaction existingTx =
        createLedgerTransaction(4L, wallet, TransactionType.DEBIT, debitAmount, idempotencyKey);

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.of(existingTx));

    // When
    WalletOperationResponse response = walletService.debit(playerId, idempotencyKey, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.transactionId()).isEqualTo(4L);
    assertThat(response.type()).isEqualTo("DEBIT");
    verify(walletRepository, never()).findByPlayerIdForUpdate(anyLong());
    verify(walletRepository, never()).save(any());
    verify(ledgerTransactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("getBalance_whenValidPlayerId_returnsCorrectBalance")
  void getBalance_whenValidPlayerId_returnsCorrectBalance() {
    // Given
    Long playerId = 1L;
    BigDecimal balance = new BigDecimal("250.75");

    Wallet wallet = createWallet(playerId, balance);
    when(walletRepository.findByPlayerId(playerId)).thenReturn(Optional.of(wallet));

    // When
    BalanceResponse response = walletService.getBalance(playerId);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.playerId()).isEqualTo(playerId);
    assertThat(response.balance()).isEqualByComparingTo(balance);
    assertThat(response.currency()).isEqualTo("COIN");
    verify(walletRepository).findByPlayerId(playerId);
  }

  @Test
  @DisplayName("getBalance_whenWalletNotFound_throwsWalletNotFoundException")
  void getBalance_whenWalletNotFound_throwsWalletNotFoundException() {
    // Given
    Long playerId = 999L;
    when(walletRepository.findByPlayerId(playerId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> walletService.getBalance(playerId))
        .isInstanceOf(WalletNotFoundException.class);
  }

  @Test
  @DisplayName("creditWallet_withDecimalAmount_preservesMonetaryPrecision")
  void creditWallet_withDecimalAmount_preservesMonetaryPrecision() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "credit-key-decimal";
    BigDecimal creditAmount = new BigDecimal("0.01");
    BigDecimal initialBalance = new BigDecimal("0.00");

    Wallet wallet = createWallet(playerId, initialBalance);
    WalletOperationRequest request =
        new WalletOperationRequest(creditAmount, "decimal-ref", "decimal credit");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
    when(ledgerTransactionRepository.save(any(LedgerTransaction.class)))
        .thenAnswer(
            invocation -> {
              LedgerTransaction tx = invocation.getArgument(0);
              tx.setTransactionId(5L);
              return tx;
            });

    // When
    WalletOperationResponse response = walletService.credit(playerId, idempotencyKey, request);

    // Then
    assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("0.01"));
    assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  @DisplayName("creditWallet_withVeryLargeAmount_handlesLargeValues")
  void creditWallet_withVeryLargeAmount_handlesLargeValues() {
    // Given
    Long playerId = 1L;
    String idempotencyKey = "credit-key-large";
    BigDecimal creditAmount = new BigDecimal("999999999.99");
    BigDecimal initialBalance = new BigDecimal("0.00");

    Wallet wallet = createWallet(playerId, initialBalance);
    WalletOperationRequest request =
        new WalletOperationRequest(creditAmount, "large-ref", "large credit");

    when(ledgerTransactionRepository.findByRequestId(idempotencyKey))
        .thenReturn(Optional.empty());
    when(walletRepository.findByPlayerIdForUpdate(playerId)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
    when(ledgerTransactionRepository.save(any(LedgerTransaction.class)))
        .thenAnswer(
            invocation -> {
              LedgerTransaction tx = invocation.getArgument(0);
              tx.setTransactionId(6L);
              return tx;
            });

    // When
    WalletOperationResponse response = walletService.credit(playerId, idempotencyKey, request);

    // Then
    assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("999999999.99"));
  }

  // Helper methods
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

  private LedgerTransaction createLedgerTransaction(
      Long transactionId,
      Wallet wallet,
      TransactionType type,
      BigDecimal amount,
      String requestId) {
    LedgerTransaction tx = new LedgerTransaction();
    tx.setTransactionId(transactionId);
    tx.setWallet(wallet);
    tx.setType(type);
    tx.setAmount(amount);
    tx.setRequestId(requestId);
    tx.setBalanceBefore(wallet.getBalance());
    tx.setBalanceAfter(wallet.getBalance().add(amount));
    tx.setReference("test-ref");
    tx.setDescription("test transaction");
    tx.setCreatedAt(Instant.now());
    return tx;
  }
}