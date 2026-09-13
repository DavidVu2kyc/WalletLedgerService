package com.example.wallet.application.mapper;

import com.example.wallet.application.dto.WalletOperationResponse;
import com.example.wallet.domain.model.LedgerTransaction;
import org.springframework.stereotype.Component;

@Component
public class WalletOperationMapper {

  public WalletOperationResponse toResponse(LedgerTransaction transaction) {
    return new WalletOperationResponse(
        transaction.getTransactionId(),
        transaction.getWallet().getPlayerId(),
        transaction.getType().toString(),
        transaction.getAmount(),
        transaction.getBalanceBefore(),
        transaction.getBalanceAfter(),
        transaction.getReference(),
        transaction.getCreatedAt());
  }
}
