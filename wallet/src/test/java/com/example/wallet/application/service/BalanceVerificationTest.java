package com.example.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.wallet.application.dto.BalanceVerificationResponse;
import com.example.wallet.application.mapper.WalletOperationMapper;
import com.example.wallet.application.service.impl.WalletServiceImpl;
import com.example.wallet.domain.model.TransactionType;
import com.example.wallet.domain.model.Wallet;
import com.example.wallet.infrastructure.persistence.repository.LedgerTransactionRepository;
import com.example.wallet.infrastructure.persistence.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceVerificationTest {

  @Mock private WalletRepository walletRepository;
  @Mock private LedgerTransactionRepository ledgerTransactionRepository;

  private WalletService walletService;

  @BeforeEach
  void setUp() {
    walletService =
        new WalletServiceImpl(
            walletRepository, ledgerTransactionRepository, new WalletOperationMapper());
  }

  @Test
  void verifyBalance_whenWalletMatchesLedger_reportsMatch() {
    Wallet wallet = new Wallet();
    wallet.setPlayerId(1L);
    wallet.setBalance(new BigDecimal("70.00"));
    when(walletRepository.findByPlayerId(1L)).thenReturn(Optional.of(wallet));
    when(ledgerTransactionRepository.sumAmountByPlayerIdAndType(1L, TransactionType.CREDIT))
        .thenReturn(new BigDecimal("100.00"));
    when(ledgerTransactionRepository.sumAmountByPlayerIdAndType(1L, TransactionType.DEBIT))
        .thenReturn(new BigDecimal("30.00"));

    BalanceVerificationResponse response = walletService.verifyBalance(1L);

    assertThat(response.walletBalance()).isEqualByComparingTo("70.00");
    assertThat(response.ledgerBalance()).isEqualByComparingTo("70.00");
    assertThat(response.matches()).isTrue();
  }

  @Test
  void verifyBalance_whenWalletDiffersFromLedger_reportsMismatch() {
    Wallet wallet = new Wallet();
    wallet.setPlayerId(1L);
    wallet.setBalance(new BigDecimal("69.00"));
    when(walletRepository.findByPlayerId(1L)).thenReturn(Optional.of(wallet));
    when(ledgerTransactionRepository.sumAmountByPlayerIdAndType(1L, TransactionType.CREDIT))
        .thenReturn(new BigDecimal("100.00"));
    when(ledgerTransactionRepository.sumAmountByPlayerIdAndType(1L, TransactionType.DEBIT))
        .thenReturn(new BigDecimal("30.00"));

    BalanceVerificationResponse response = walletService.verifyBalance(1L);

    assertThat(response.matches()).isFalse();
  }
}
