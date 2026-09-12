package com.example.wallet.application.service;

import com.example.wallet.application.dto.CreditWalletCommand;
import com.example.wallet.application.dto.DebitWalletCommand;
import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.domain.model.TransactionType;
import com.example.wallet.domain.model.Wallet;
import java.math.BigDecimal;
import java.time.Instant;

public class LedgerFactory {

  public static LedgerTransaction credit(
      Wallet wallet, CreditWalletCommand command, BigDecimal balanceBefore) {

    BigDecimal balanceAfter = balanceBefore.add(command.amount());

    LedgerTransaction tx = new LedgerTransaction();
    tx.setWallet(wallet);
    tx.setRequestId(command.requestId());
    tx.setType(TransactionType.CREDIT);
    tx.setAmount(command.amount());
    tx.setBalanceBefore(balanceBefore);
    tx.setBalanceAfter(balanceAfter);
    tx.setReference(command.reference());
    tx.setDescription(command.description());
    tx.setCreatedAt(Instant.now());
    return tx;
  }

  public static LedgerTransaction debit(
      Wallet wallet, DebitWalletCommand command, BigDecimal balanceBefore) {
    BigDecimal balanceAfter = balanceBefore.subtract(command.amount());

    LedgerTransaction tx = new LedgerTransaction();
    tx.setWallet(wallet);
    tx.setRequestId(command.requestId());
    tx.setType(TransactionType.DEBIT);
    tx.setAmount(command.amount());
    tx.setBalanceBefore(balanceBefore);
    tx.setBalanceAfter(balanceAfter);
    tx.setReference(command.reference());
    tx.setDescription(command.description());
    tx.setCreatedAt(Instant.now());
    return tx;
  }
}
