package com.example.wallet.interfaces.rest;

import com.example.wallet.application.dto.TransactionPage;
import com.example.wallet.application.service.TransactionQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class TransactionController {

  private final TransactionQueryService transactionQueryService;

  public TransactionController(TransactionQueryService transactionQueryService) {
    this.transactionQueryService = transactionQueryService;
  }

  @GetMapping("/{playerId}/transactions")
  public ResponseEntity<TransactionPage> getTransactionHistory(
      @PathVariable Long playerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    TransactionPage transactionPage = transactionQueryService.getTransactions(playerId, page, size);
    return ResponseEntity.ok(transactionPage);
  }
}
