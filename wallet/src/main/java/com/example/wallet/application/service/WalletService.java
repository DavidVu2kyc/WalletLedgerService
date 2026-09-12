package com.example.wallet.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.wallet.application.dto.BalanceResponse;
import com.example.wallet.application.dto.CreditWalletCommand;
import com.example.wallet.application.dto.DebitWalletCommand;
import com.example.wallet.application.dto.WalletBalanceResponse;
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
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class WalletService {

  private static final String OPERATION_COUNTER = "wallet.operation";
  private static final String STATUS_SUCCESS = "success";
  private static final String STATUS_INSUFFICIENT_FUNDS = "insufficient_funds";
  private static final String STATUS_ERROR = "error";

  private final WalletRepository walletRepository;
  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final MeterRegistry meterRegistry;

  public WalletService(
      WalletRepository walletRepository,
      LedgerTransactionRepository ledgerTransactionRepository,
      MeterRegistry meterRegistry) {
    this.walletRepository = walletRepository;
    this.ledgerTransactionRepository = ledgerTransactionRepository;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public WalletOperationResponse credit(
      Long playerId, String idempotencyKey, WalletOperationRequest request) {
    log.info(
        "Credit request received",
        kv("playerId", playerId),
        kv("amount", request.amount()),
        kv("reference", request.reference()));

    // Check if already processed with same idempotency key
    var existingTx = ledgerTransactionRepository.findByRequestId(idempotencyKey);
    if (existingTx.isPresent()) {
      verifyIdempotentPayload(existingTx.get(), request, TransactionType.CREDIT);
      recordOperation("credit", STATUS_SUCCESS);
      log.info(
          "Credit idempotent replay",
          kv("playerId", playerId),
          kv("transactionId", existingTx.get().getTransactionId()));
      return toOperationResponse(existingTx.get());
    }

    // Find wallet with pessimistic lock to serialize concurrent operations on the same wallet
    Wallet wallet =
        walletRepository
            .findByPlayerIdForUpdate(playerId)
            .orElseThrow(
                () -> {
                  recordOperation("credit", STATUS_ERROR);
                  log.warn(
                      "Credit failed: wallet not found",
                      kv("playerId", playerId),
                      kv("requestId", idempotencyKey));
                  return new WalletNotFoundException(
                      String.format("Wallet not found for playerId: %d", playerId));
                });

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
        recordOperation("credit", STATUS_SUCCESS);
        log.info(
            "Credit idempotent replay after conflict",
            kv("playerId", playerId),
            kv("transactionId", existingTransaction.get().getTransactionId()));
        return toOperationResponse(existingTransaction.get());
      }
      recordOperation("credit", STATUS_ERROR);
      log.error(
          "Credit failed: data integrity violation",
          kv("playerId", playerId),
          kv("amount", request.amount()),
          kv("requestId", idempotencyKey),
          e);
      throw new IdeIdempotencyKeyConflictException(
          "Idempotency key already used with different parameters");
    }

    recordOperation("credit", STATUS_SUCCESS);
    log.info(
        "Credit successful",
        kv("playerId", playerId),
        kv("amount", request.amount()),
        kv("transactionId", tx.getTransactionId()),
        kv("balanceAfter", tx.getBalanceAfter()));
    return toOperationResponse(tx);
  }

  @Transactional
  public WalletOperationResponse debit(
      Long playerId, String idempotencyKey, WalletOperationRequest request) {
    log.info(
        "Debit request received",
        kv("playerId", playerId),
        kv("amount", request.amount()),
        kv("reference", request.reference()));

    // Check if already processed with same idempotency key
    var existingTx = ledgerTransactionRepository.findByRequestId(idempotencyKey);
    if (existingTx.isPresent()) {
      verifyIdempotentPayload(existingTx.get(), request, TransactionType.DEBIT);
      recordOperation("debit", STATUS_SUCCESS);
      log.info(
          "Debit idempotent replay",
          kv("playerId", playerId),
          kv("transactionId", existingTx.get().getTransactionId()));
      return toOperationResponse(existingTx.get());
    }

    // Find wallet with pessimistic lock to serialize concurrent operations on the same wallet
    Wallet wallet =
        walletRepository
            .findByPlayerIdForUpdate(playerId)
            .orElseThrow(
                () -> {
                  recordOperation("debit", STATUS_ERROR);
                  log.warn(
                      "Debit failed: wallet not found",
                      kv("playerId", playerId),
                      kv("requestId", idempotencyKey));
                  return new WalletNotFoundException(
                      String.format("Wallet not found for playerId: %d", playerId));
                });

    // Check sufficient balance before trying to debit
    if (wallet.getBalance().compareTo(request.amount()) < 0) {
      recordOperation("debit", STATUS_INSUFFICIENT_FUNDS);
      log.warn(
          "Debit failed: insufficient balance",
          kv("playerId", playerId),
          kv("amount", request.amount()),
          kv("balance", wallet.getBalance()));
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
        recordOperation("debit", STATUS_SUCCESS);
        log.info(
            "Debit idempotent replay after conflict",
            kv("playerId", playerId),
            kv("transactionId", existingTransaction.get().getTransactionId()));
        return toOperationResponse(existingTransaction.get());
      }
      recordOperation("debit", STATUS_ERROR);
      log.error(
          "Debit failed: data integrity violation",
          kv("playerId", playerId),
          kv("amount", request.amount()),
          kv("requestId", idempotencyKey),
          e);
      throw new IdeIdempotencyKeyConflictException(
          "Idempotency key already used with different parameters");
    }

    recordOperation("debit", STATUS_SUCCESS);
    log.info(
        "Debit successful",
        kv("playerId", playerId),
        kv("amount", request.amount()),
        kv("transactionId", tx.getTransactionId()),
        kv("balanceAfter", tx.getBalanceAfter()));
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

  @Transactional(readOnly = true)
  public WalletBalanceResponse auditBalance(Long playerId) {
    Wallet wallet =
        walletRepository
            .findByPlayerId(playerId)
            .orElseThrow(
                () ->
                    new WalletNotFoundException(
                        String.format("Wallet not found for playerId: %d", playerId)));

    List<LedgerTransaction> transactions = ledgerTransactionRepository.findByWallet(wallet);

    BigDecimal ledgerSum =
        transactions.stream()
            .map(
                tx ->
                    tx.getType() == TransactionType.CREDIT
                        ? tx.getAmount()
                        : tx.getAmount().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal currentBalance = wallet.getBalance();
    boolean isConsistent = currentBalance.compareTo(ledgerSum) == 0;

    log.info(
        "Balance audit performed",
        kv("playerId", playerId),
        kv("isConsistent", isConsistent),
        kv("currentBalance", currentBalance),
        kv("ledgerSum", ledgerSum));

    return new WalletBalanceResponse(playerId, currentBalance, ledgerSum, isConsistent);
  }

  private void recordOperation(String type, String status) {
    meterRegistry.counter(OPERATION_COUNTER, "type", type, "status", status).increment();
  }

  private void verifyIdempotentPayload(
      LedgerTransaction existing, WalletOperationRequest request, TransactionType expectedType) {
    boolean sameAmount = existing.getAmount().compareTo(request.amount()) == 0;
    boolean sameType = existing.getType() == expectedType;
    boolean sameRef = Objects.equals(existing.getReference(), request.reference());

    if (!sameAmount || !sameType || !sameRef) {
      recordOperation(expectedType.name().toLowerCase(), STATUS_ERROR);
      log.warn(
          "Idempotency key reused with different payload",
          kv("requestId", existing.getRequestId()),
          kv("type", expectedType));
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