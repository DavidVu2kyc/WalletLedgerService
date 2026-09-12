package com.example.wallet.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WalletOperationRequest(
    @NotNull @Positive @DecimalMin(value = "0.01", message = "Amount must be greater than zero") BigDecimal amount,
    @NotBlank(message = "Reference is required") @Size(max = 100) String reference,
    @Size(max = 255) String description) {}
