package com.example.wallet.application.dto;

import java.math.BigDecimal;

/** Result of reconciling a wallet's materialized balance with its ledger entries. */
public record BalanceVerificationResponse(
    Long playerId, BigDecimal walletBalance, BigDecimal ledgerBalance, boolean matches) {}
