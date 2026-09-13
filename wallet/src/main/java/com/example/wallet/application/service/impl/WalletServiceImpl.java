package com.example.wallet.application.service.impl;

import com.example.wallet.application.dto.BalanceVerificationResponse;
import com.example.wallet.application.dto.CreditWalletCommand;
import com.example.wallet.application.dto.DebitWalletCommand;
import com.example.wallet.application.dto.WalletBalanceResponse;
import com.example.wallet.application.dto.WalletOperationRequest;
import com.example.wallet.application.dto.WalletOperationResponse;
import com.example.wallet.application.mapper.WalletOperationMapper;
import com.example.wallet.application.service.LedgerFactory;
import com.example.wallet.application.service.WalletService;
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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

  private final WalletRepository walletRepository;
  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final WalletOperationMapper walletOperationMapper;

  @Override
  @Transactional
  public WalletOperationResponse credit(
      Long playerId, String idempotencyKey, WalletOperationRequest request) {
    var existingTransaction = ledgerTransactionRepository.findByRequestId(idempotencyKey);
    if (existingTransaction.isPresent()) {
      verifyIdempotentPayload(existingTransaction.get(), request, TransactionType.CREDIT);
      return walletOperationMapper.toResponse(existingTransaction.get());
    }

    Wallet wallet = findWalletForUpdate(playerId);
    BigDecimal balanceBefore = wallet.getBalance();
    wallet.credit(request.amount());
    wallet.setUpdatedAt(Instant.now());

    LedgerTransaction transaction =
        LedgerFactory.credit(
            wallet,
            new CreditWalletCommand(
                playerId,
                idempotencyKey,
                request.amount(),
                request.reference(),
                request.description()),
            balanceBefore);

    return saveOrReturnExisting(transaction, request, TransactionType.CREDIT);
  }

  @Override
  @Transactional
  public WalletOperationResponse debit(
      Long playerId, String idempotencyKey, WalletOperationRequest request) {
    var existingTransaction = ledgerTransactionRepository.findByRequestId(idempotencyKey);
    if (existingTransaction.isPresent()) {
      verifyIdempotentPayload(existingTransaction.get(), request, TransactionType.DEBIT);
      return walletOperationMapper.toResponse(existingTransaction.get());
    }

    Wallet wallet = findWalletForUpdate(playerId);
    if (wallet.getBalance().compareTo(request.amount()) < 0) {
      throw new InsufficientBalanceException("Wallet balance is insufficient for debit operation");
    }

    BigDecimal balanceBefore = wallet.getBalance();
    wallet.debit(request.amount());
    wallet.setUpdatedAt(Instant.now());

    LedgerTransaction transaction =
        LedgerFactory.debit(
            wallet,
            new DebitWalletCommand(
                playerId,
                idempotencyKey,
                request.amount(),
                request.reference(),
                request.description()),
            balanceBefore);

    return saveOrReturnExisting(transaction, request, TransactionType.DEBIT);
  }

  @Override
  @Transactional(readOnly = true)
  public WalletBalanceResponse getBalance(Long playerId) {
    Wallet wallet = findWallet(playerId);
    return new WalletBalanceResponse(wallet.getPlayerId(), wallet.getBalance(), "COIN");
  }

  @Override
  @Transactional(readOnly = true)
  public BalanceVerificationResponse verifyBalance(Long playerId) {
    Wallet wallet = findWallet(playerId);
    BigDecimal credits =
        ledgerTransactionRepository.sumAmountByPlayerIdAndType(playerId, TransactionType.CREDIT);
    BigDecimal debits =
        ledgerTransactionRepository.sumAmountByPlayerIdAndType(playerId, TransactionType.DEBIT);
    BigDecimal ledgerBalance = credits.subtract(debits);

    return new BalanceVerificationResponse(
        wallet.getPlayerId(),
        wallet.getBalance(),
        ledgerBalance,
        wallet.getBalance().compareTo(ledgerBalance) == 0);
  }

  private WalletOperationResponse saveOrReturnExisting(
      LedgerTransaction transaction, WalletOperationRequest request, TransactionType expectedType) {
    try {
      walletRepository.save(transaction.getWallet());
      ledgerTransactionRepository.save(transaction);
      return walletOperationMapper.toResponse(transaction);
    } catch (DataIntegrityViolationException exception) {
      return ledgerTransactionRepository
          .findByRequestId(transaction.getRequestId())
          .map(
              existingTransaction -> {
                verifyIdempotentPayload(existingTransaction, request, expectedType);
                return walletOperationMapper.toResponse(existingTransaction);
              })
          .orElseThrow(
              () ->
                  new IdeIdempotencyKeyConflictException(
                      "Idempotency key already used with different parameters"));
    }
  }

  private Wallet findWalletForUpdate(Long playerId) {
    return walletRepository
        .findByPlayerIdForUpdate(playerId)
        .orElseThrow(() -> walletNotFound(playerId));
  }

  private Wallet findWallet(Long playerId) {
    return walletRepository.findByPlayerId(playerId).orElseThrow(() -> walletNotFound(playerId));
  }

  private WalletNotFoundException walletNotFound(Long playerId) {
    return new WalletNotFoundException(String.format("Wallet not found for playerId: %d", playerId));
  }

  private void verifyIdempotentPayload(
      LedgerTransaction existingTransaction,
      WalletOperationRequest request,
      TransactionType expectedType) {
    boolean sameAmount = existingTransaction.getAmount().compareTo(request.amount()) == 0;
    boolean sameType = existingTransaction.getType() == expectedType;
    boolean sameReference = Objects.equals(existingTransaction.getReference(), request.reference());

    if (!sameAmount || !sameType || !sameReference) {
      throw new IdeIdempotencyKeyConflictException(
          "Idempotency key already used with different parameters");
    }
  }
}
