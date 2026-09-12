package com.example.wallet.interfaces.rest;

import com.example.wallet.application.dto.BalanceResponse;
import com.example.wallet.application.dto.WalletOperationRequest;
import com.example.wallet.application.dto.WalletOperationResponse;
import com.example.wallet.application.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

  private final WalletService walletService;

  public WalletController(WalletService walletService) {
    this.walletService = walletService;
  }

  @PostMapping("/{playerId}/credit")
  public ResponseEntity<WalletOperationResponse> credit(
      @PathVariable Long playerId,
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody WalletOperationRequest request) {
    WalletOperationResponse response = walletService.credit(playerId, idempotencyKey, request);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @PostMapping("/{playerId}/debit")
  public ResponseEntity<WalletOperationResponse> debit(
      @PathVariable Long playerId,
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody WalletOperationRequest request) {
    WalletOperationResponse response = walletService.debit(playerId, idempotencyKey, request);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @GetMapping("/{playerId}/balance")
  public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long playerId) {
    return ResponseEntity.ok(walletService.getBalance(playerId));
  }
}
