package com.example.wallet.application.service;

import com.example.wallet.application.dto.TransactionPage;
public interface TransactionQueryService {

  TransactionPage getTransactions(Long playerId, int page, int size);
}
