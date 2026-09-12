package com.example.wallet.application.dto;

import java.math.BigDecimal;

public record BalanceResponse(Long playerId, BigDecimal balance, String currency) {}
