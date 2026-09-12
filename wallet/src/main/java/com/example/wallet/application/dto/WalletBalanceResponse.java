package com.example.wallet.application.dto;

import java.math.BigDecimal;

public record WalletBalanceResponse(Long playerId, BigDecimal balance, String currency) {}
