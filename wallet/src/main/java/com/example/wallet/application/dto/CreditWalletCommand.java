package com.example.wallet.application.dto;

import java.math.BigDecimal;

public record CreditWalletCommand(
    Long playerId, String requestId, BigDecimal amount, String reference, String description) {}
