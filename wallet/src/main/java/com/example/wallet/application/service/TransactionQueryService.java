package com.example.wallet.application.service;

import com.example.wallet.application.dto.TransactionPage;
import com.example.wallet.application.dto.TransactionResponse;
import com.example.wallet.domain.exception.WalletNotFoundException;
import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.infrastructure.persistence.repository.LedgerTransactionRepository;
import com.example.wallet.infrastructure.persistence.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final WalletRepository walletRepository;

  @Transactional(readOnly = true)
  public TransactionPage getTransactions(Long playerId, int page, int size) {
    // Verify wallet exists
    walletRepository
        .findByPlayerId(playerId)
        .orElseThrow(
            () ->
                new WalletNotFoundException(
                    String.format("Wallet not found for playerId: %d", playerId)));

    // Validate pagination parameters
    size = Math.max(1, Math.min(size, 100)); // size must be between 1 and 100
    page = Math.max(0, page); // page must be >= 0

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

    Page<LedgerTransaction> transactions =
        ledgerTransactionRepository.findByWalletPlayerId(playerId, pageable);

    return new TransactionPage(
        transactions.getContent().stream().map(this::toTransactionResponse).toList(),
        transactions.getNumber(),
        transactions.getSize(),
        transactions.getTotalElements(),
        transactions.getTotalPages(),
        transactions.isFirst(),
        transactions.isLast());
  }

  private TransactionResponse toTransactionResponse(LedgerTransaction tx) {
    return new TransactionResponse(
        tx.getTransactionId(),
        tx.getWallet().getPlayerId(),
        tx.getType().toString(),
        tx.getAmount(),
        tx.getBalanceBefore(),
        tx.getBalanceAfter(),
        tx.getReference(),
        tx.getDescription(),
        tx.getCreatedAt());
  }
}
