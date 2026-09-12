package com.example.wallet.application.dto;

import java.util.List;

public record TransactionPage(
    List<TransactionResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {}
