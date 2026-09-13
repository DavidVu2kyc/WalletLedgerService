package com.example.wallet.application.service.impl;

import com.example.wallet.application.dto.TransactionPage;
import com.example.wallet.application.mapper.TransactionMapper;
import com.example.wallet.application.service.TransactionQueryService;
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
public class TransactionQueryServiceImpl implements TransactionQueryService {

  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final WalletRepository walletRepository;
  private final TransactionMapper transactionMapper;

  @Override
  @Transactional(readOnly = true)
  public TransactionPage getTransactions(Long playerId, int page, int size) {
    walletRepository
        .findByPlayerId(playerId)
        .orElseThrow(
            () ->
                new WalletNotFoundException(
                    String.format("Wallet not found for playerId: %d", playerId)));

    int sanitizedSize = Math.max(1, Math.min(size, 100));
    int sanitizedPage = Math.max(0, page);
    Pageable pageable =
        PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<LedgerTransaction> transactions =
        ledgerTransactionRepository.findByWalletPlayerId(playerId, pageable);

    return new TransactionPage(
        transactions.getContent().stream().map(transactionMapper::toResponse).toList(),
        transactions.getNumber(),
        transactions.getSize(),
        transactions.getTotalElements(),
        transactions.getTotalPages(),
        transactions.isFirst(),
        transactions.isLast());
  }
}
