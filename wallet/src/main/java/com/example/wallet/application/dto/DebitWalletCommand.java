package com.example.wallet.application.dto;

import java.math.BigDecimal;

public record DebitWalletCommand(
    Long playerId, String requestId, BigDecimal amount, String reference, String description) {}
