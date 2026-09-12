package com.example.wallet.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletOperationResponse(
    Long transactionId,
    Long playerId,
    String type,
    BigDecimal amount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    String reference,
    Instant createdAt) {}
