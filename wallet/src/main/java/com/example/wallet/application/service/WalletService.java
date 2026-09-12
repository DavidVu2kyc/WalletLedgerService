package com.example.wallet.application.service;

import com.example.wallet.domain.model.TransactionType;
import java.util.Objects;
import com.example.wallet.application.dto.BalanceResponse;
import com.example.wallet.application.dto.CreditWalletCommand;
import com.example.wallet.application.dto.DebitWalletCommand;
import com.example.wallet.application.dto.WalletOperationRequest;
import com.example.wallet.application.dto.WalletOperationResponse;
import com.example.wallet.domain.exception.IdeIdempotencyKeyConflictException;
import com.example.wallet.domain.exception.InsufficientBalanceException;
import com.example.wallet.domain.exception.WalletNotFoundException;
import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.domain.model.Wallet;
import com.example.wallet.infrastructure.persistence.repository.LedgerTransactionRepository;
import com.example.wallet.infrastructure.persistence.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

  private final WalletRepository walletRepository;
  private final LedgerTransactionRepository ledgerTransactionRepository;

  @Transactional
  public WalletOperationResponse credit(
      Long playerId, String idempotencyKey, WalletOperationRequest request) {

    // Check if already processed with same idempotency key
    var existingTx = ledgerTransactionRepository.findByRequestId(idempotencyKey);
    if (existingTx.isPresent()) {
      verifyIdempotentPayload(existingTx.get(), request, TransactionType.CREDIT);
      return toOperationResponse(existingTx.get());
    }

    // Find wallet with pessimistic lock to serialize concurrent operations on the same wallet
    Wallet wallet =
        walletRepository
            .findByPlayerIdForUpdate(playerId)
            .orElseThrow(
                () ->
                    new WalletNotFoundException(
                        String.format("Wallet not found for playerId: %d", playerId)));

    BigDecimal balanceBefore = wallet.getBalance();

    // Apply credit
    wallet.credit(request.amount());
    wallet.setUpdatedAt(Instant.now());

    // Create ledger transaction
    CreditWalletCommand command =
        new CreditWalletCommand(
            playerId, idempotencyKey, request.amount(), request.reference(), request.description());
    LedgerTransaction tx = LedgerFactory.credit(wallet, command, balanceBefore);

    try {
      // Save wallet first (will trigger version check)
      walletRepository.save(wallet);
      // Then save transaction (unique constraint on request_id ensures idempotency)
      ledgerTransactionRepository.save(tx);
    } catch (DataIntegrityViolationException e) {
      // If request_id already exists, it means idempotency key was reused
      // Check if it's the same amount/reference or different
      var existingTransaction = ledgerTransactionRepository.findByRequestId(idempotencyKey);
      if (existingTransaction.isPresent()) {
        verifyIdempotentPayload(existingTransaction.get(), request, TransactionType.CREDIT);
        return toOperationResponse(existingTransaction.get());
      }
      throw new IdeIdempotencyKeyConflictException(
          "Idempotency key already used with different parameters");
    }

    return toOperationResponse(tx);
  }

  @Transactional
  public WalletOperationResponse debit(
      Long playerId, String idempotencyKey, WalletOperationRequest request) {

    // Check if already processed with same idempotency key
    var existingTx = ledgerTransactionRepository.findByRequestId(idempotencyKey);
    if (existingTx.isPresent()) {
      verifyIdempotentPayload(existingTx.get(), request, TransactionType.DEBIT);
      return toOperationResponse(existingTx.get());
    }

    // Find wallet with pessimistic lock to serialize concurrent operations on the same wallet
    Wallet wallet =
        walletRepository
            .findByPlayerIdForUpdate(playerId)
            .orElseThrow(
                () ->
                    new WalletNotFoundException(
                        String.format("Wallet not found for playerId: %d", playerId)));

    // Check sufficient balance before trying to debit
    if (wallet.getBalance().compareTo(request.amount()) < 0) {
      throw new InsufficientBalanceException("Wallet balance is insufficient for debit operation");
    }

    BigDecimal balanceBefore = wallet.getBalance();

    // Apply debit
    wallet.debit(request.amount());
    wallet.setUpdatedAt(Instant.now());

    // Create ledger transaction
    DebitWalletCommand command =
        new DebitWalletCommand(
            playerId, idempotencyKey, request.amount(), request.reference(), request.description());
    LedgerTransaction tx = LedgerFactory.debit(wallet, command, balanceBefore);

    try {
      // Save wallet first (will trigger version check for optimistic locking)
      walletRepository.save(wallet);
      // Then save transaction
      ledgerTransactionRepository.save(tx);
    } catch (DataIntegrityViolationException e) {
      // Idempotency key already exists
      var existingTransaction = ledgerTransactionRepository.findByRequestId(idempotencyKey);
      if (existingTransaction.isPresent()) {
        verifyIdempotentPayload(existingTransaction.get(), request, TransactionType.DEBIT);
        return toOperationResponse(existingTransaction.get());
      }
      throw new IdeIdempotencyKeyConflictException(
          "Idempotency key already used with different parameters");
    }

    return toOperationResponse(tx);
  }

  @Transactional(readOnly = true)
  public BalanceResponse getBalance(Long playerId) {
    Wallet wallet =
        walletRepository
            .findByPlayerId(playerId)
            .orElseThrow(
                () ->
                    new WalletNotFoundException(
                        String.format("Wallet not found for playerId: %d", playerId)));

    return new BalanceResponse(wallet.getPlayerId(), wallet.getBalance(), "COIN");
  }

  private void verifyIdempotentPayload( LedgerTransaction existing, WalletOperationRequest request, TransactionType expectedType) {
  boolean sameAmount = existing.getAmount().compareTo(request.amount()) == 0;
  boolean sameType = existing.getType() == expectedType;
  boolean sameRef = Objects.equals(existing.getReference(), request.reference());

  if (!sameAmount || !sameType || !sameRef) {
    throw new IdeIdempotencyKeyConflictException(
        "Idempotency key already used with different parameters");
  }
}


  private WalletOperationResponse toOperationResponse(LedgerTransaction tx) {
    return new WalletOperationResponse(
        tx.getTransactionId(),
        tx.getWallet().getPlayerId(),
        tx.getType().toString(),
        tx.getAmount(),
        tx.getBalanceBefore(),
        tx.getBalanceAfter(),
        tx.getReference(),
        tx.getCreatedAt());
  }
}
