package com.example.wallet.application.mapper;

import com.example.wallet.application.dto.TransactionResponse;
import com.example.wallet.domain.model.LedgerTransaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

  public TransactionResponse toResponse(LedgerTransaction transaction) {
    return new TransactionResponse(
        transaction.getTransactionId(),
        transaction.getWallet().getPlayerId(),
        transaction.getType().toString(),
        transaction.getAmount(),
        transaction.getBalanceBefore(),
        transaction.getBalanceAfter(),
        transaction.getReference(),
        transaction.getDescription(),
        transaction.getCreatedAt());
  }
}
