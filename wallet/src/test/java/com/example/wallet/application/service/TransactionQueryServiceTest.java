package com.example.wallet.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.wallet.application.dto.TransactionPage;
import com.example.wallet.domain.exception.WalletNotFoundException;
import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.domain.model.TransactionType;
import com.example.wallet.domain.model.Wallet;
import com.example.wallet.infrastructure.persistence.repository.LedgerTransactionRepository;
import com.example.wallet.infrastructure.persistence.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionQueryService Unit Tests")
class TransactionQueryServiceTest {

  @Mock private LedgerTransactionRepository ledgerTransactionRepository;

  @Mock private WalletRepository walletRepository;

  private TransactionQueryService transactionQueryService;

  @BeforeEach
  void setUp() {
    transactionQueryService =
        new TransactionQueryService(ledgerTransactionRepository, walletRepository);
  }

  @Test
  @DisplayName("getTransactions_whenValidPlayerId_returnsPaginatedTransactions")
  void testGetTransactions_ValidPlayerId() {
    // Given
    Long playerId = 1L;
    int page = 0;
    int size = 20;

    Wallet wallet = createWallet(playerId);
    LedgerTransaction tx1 =
        createLedgerTransaction(1L, wallet, TransactionType.CREDIT, "req-1");
    LedgerTransaction tx2 = createLedgerTransaction(2L, wallet, TransactionType.DEBIT, "req-2");

    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    Page<LedgerTransaction> transactionPage = new PageImpl<>(List.of(tx1, tx2), pageable, 2);

    when(walletRepository.findByPlayerId(playerId)).thenReturn(Optional.of(wallet));
    when(ledgerTransactionRepository.findByWalletPlayerId(playerId, pageable))
        .thenReturn(transactionPage);

    // When
    TransactionPage response = transactionQueryService.getTransactions(playerId, page, size);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.content()).hasSize(2);
    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(20);
    assertThat(response.totalElements()).isEqualTo(2);
    assertThat(response.totalPages()).isEqualTo(1);
    assertThat(response.first()).isTrue();
    assertThat(response.last()).isTrue();

    verify(walletRepository).findByPlayerId(playerId);
    verify(ledgerTransactionRepository).findByWalletPlayerId(playerId, pageable);
  }

  @Test
  @DisplayName("getTransactions_whenWalletNotFound_throwsWalletNotFoundException")
  void testGetTransactions_WalletNotFound() {
    // Given
    Long playerId = 999L;
    when(walletRepository.findByPlayerId(playerId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> transactionQueryService.getTransactions(playerId, 0, 20))
        .isInstanceOf(WalletNotFoundException.class);
    verify(walletRepository).findByPlayerId(playerId);
    verify(ledgerTransactionRepository, never()).findByWalletPlayerId(anyLong(), any());
  }

  @Test
  @DisplayName("getTransactions_whenEmptyTransactionHistory_returnsEmptyPage")
  void testGetTransactions_EmptyHistory() {
    // Given
    Long playerId = 1L;
    int page = 0;
    int size = 20;

    Wallet wallet = createWallet(playerId);
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<LedgerTransaction> emptyPage = new PageImpl<>(List.of(), pageable, 0);

    when(walletRepository.findByPlayerId(playerId)).thenReturn(Optional.of(wallet));
    when(ledgerTransactionRepository.findByWalletPlayerId(playerId, pageable))
        .thenReturn(emptyPage);

    // When
    TransactionPage response = transactionQueryService.getTransactions(playerId, page, size);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.content()).isEmpty();
    assertThat(response.totalElements()).isEqualTo(0);
    assertThat(response.totalPages()).isEqualTo(0);
  }

  @Test
  @DisplayName("getTransactions_whenPageSizeTooLarge_returnsMaxSize")
  void testGetTransactions_PageSizeMaxed() {
    // Given
    Long playerId = 1L;
    int page = 0;
    int requestedSize = 200; // Larger than max allowed (100)

    Wallet wallet = createWallet(playerId);
    Pageable pageable = PageRequest.of(page, 100, Sort.by("createdAt").descending());
    Page<LedgerTransaction> transactionPage = new PageImpl<>(List.of(), pageable, 0);

    when(walletRepository.findByPlayerId(playerId)).thenReturn(Optional.of(wallet));
    when(ledgerTransactionRepository.findByWalletPlayerId(playerId, pageable))
        .thenReturn(transactionPage);

    // When
    TransactionPage response =
        transactionQueryService.getTransactions(playerId, page, requestedSize);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.size()).isLessThanOrEqualTo(100);
  }

  @Test
  @DisplayName("getTransactions_withMultiplePages_indicatesFirstAndLast")
  void testGetTransactions_MultiplePages() {
    // Given
    Long playerId = 1L;
    Wallet wallet = createWallet(playerId);

    // Page 0
    Pageable pageZero = PageRequest.of(0, 20, Sort.by("createdAt").descending());
    LedgerTransaction tx1 =
        createLedgerTransaction(1L, wallet, TransactionType.CREDIT, "req-1");
    Page<LedgerTransaction> firstPage = new PageImpl<>(List.of(tx1), pageZero, 40);

    when(walletRepository.findByPlayerId(playerId)).thenReturn(Optional.of(wallet));
    when(ledgerTransactionRepository.findByWalletPlayerId(playerId, pageZero))
        .thenReturn(firstPage);

    // When
    TransactionPage response = transactionQueryService.getTransactions(playerId, 0, 20);

    // Then
    assertThat(response.first()).isTrue();
    assertThat(response.last()).isFalse();
    assertThat(response.totalPages()).isEqualTo(2);
  }

  // Helper methods
  private Wallet createWallet(Long playerId) {
    Wallet wallet = new Wallet();
    wallet.setWalletId(1L);
    wallet.setPlayerId(playerId);
    wallet.setBalance(BigDecimal.ZERO);
    wallet.setVersion(0L);
    wallet.setCreatedAt(Instant.now());
    wallet.setUpdatedAt(Instant.now());
    return wallet;
  }

  private LedgerTransaction createLedgerTransaction(
      Long transactionId,
      Wallet wallet,
      TransactionType type,
      String requestId) {
    LedgerTransaction tx = new LedgerTransaction();
    tx.setTransactionId(transactionId);
    tx.setWallet(wallet);
    tx.setType(type);
    tx.setAmount(BigDecimal.TEN);
    tx.setRequestId(requestId);
    tx.setBalanceBefore(BigDecimal.ZERO);
    tx.setBalanceAfter(BigDecimal.TEN);
    tx.setReference("test-ref");
    tx.setDescription("test transaction");
    tx.setCreatedAt(Instant.now());
    return tx;
  }
}
