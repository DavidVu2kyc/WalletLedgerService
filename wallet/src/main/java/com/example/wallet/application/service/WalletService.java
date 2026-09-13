package com.example.wallet.application.service;

import com.example.wallet.application.dto.BalanceVerificationResponse;
import com.example.wallet.application.dto.WalletBalanceResponse;
import com.example.wallet.application.dto.WalletOperationRequest;
import com.example.wallet.application.dto.WalletOperationResponse;
public interface WalletService {

  WalletOperationResponse credit(Long playerId, String idempotencyKey, WalletOperationRequest request);

  WalletOperationResponse debit(Long playerId, String idempotencyKey, WalletOperationRequest request);

  WalletBalanceResponse getBalance(Long playerId);

  BalanceVerificationResponse verifyBalance(Long playerId);
}
