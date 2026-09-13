package com.example.wallet.integration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.application.dto.WalletBalanceResponse;
import com.example.wallet.application.dto.WalletOperationRequest;
import com.example.wallet.application.dto.WalletOperationResponse;
import com.example.wallet.application.service.WalletService;
import com.example.wallet.domain.exception.DuplicateRequestException;
import com.example.wallet.domain.exception.IdeIdempotencyKeyConflictException;
import com.example.wallet.domain.exception.InsufficientBalanceException;
import com.example.wallet.domain.exception.WalletNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.example.wallet.interfaces.rest.WalletController;

@WebMvcTest(WalletController.class)
@DisplayName("WalletController API Tests")
class WalletControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private WalletService walletService;

  private static final String IDEMPOTENCY_KEY = "test-idempotency-key";

  @Test
  @DisplayName("credit_whenValidRequest_returns200WithOperationResponse")
  void credit_whenValidRequest_returns200WithOperationResponse() throws Exception {
    WalletOperationResponse response =
        new WalletOperationResponse(
            1L, 1L, "CREDIT", new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("150.00"),
            "ref-123", Instant.parse("2026-01-01T00:00:00Z"));

    when(walletService.credit(eq(1L), eq(IDEMPOTENCY_KEY), org.mockito.ArgumentMatchers.any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/wallets/1/credit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("50.00"), "ref-123", "credit"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value(1))
        .andExpect(jsonPath("$.playerId").value(1))
        .andExpect(jsonPath("$.type").value("CREDIT"))
        .andExpect(jsonPath("$.amount").value(50.00))
        .andExpect(jsonPath("$.balanceBefore").value(100.00))
        .andExpect(jsonPath("$.balanceAfter").value(150.00))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());

    verify(walletService)
        .credit(
            eq(1L),
            eq(IDEMPOTENCY_KEY),
            org.mockito.ArgumentMatchers.any(WalletOperationRequest.class));
  }

  @Test
  @DisplayName("debit_whenValidRequest_returns200WithOperationResponse")
  void debit_whenValidRequest_returns200WithOperationResponse() throws Exception {
    WalletOperationResponse response =
        new WalletOperationResponse(
            2L, 1L, "DEBIT", new BigDecimal("30.00"), new BigDecimal("100.00"), new BigDecimal("70.00"),
            "ref-456", Instant.parse("2026-01-01T01:00:00Z"));

    when(walletService.debit(eq(1L), eq(IDEMPOTENCY_KEY), org.mockito.ArgumentMatchers.any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/wallets/1/debit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("30.00"), "ref-456", "debit"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value(2))
        .andExpect(jsonPath("$.type").value("DEBIT"))
        .andExpect(jsonPath("$.balanceAfter").value(70.00));

    verify(walletService)
        .debit(
            eq(1L),
            eq(IDEMPOTENCY_KEY),
            org.mockito.ArgumentMatchers.any(WalletOperationRequest.class));
  }

  @Test
  @DisplayName("credit_whenZeroAmount_returns400ValidationError")
  void credit_whenZeroAmount_returns400ValidationError() throws Exception {
    String body =
        "{\"amount\":0,\"reference\":\"ref\",\"description\":\"zero\"}";

    mockMvc
        .perform(
            post("/api/v1/wallets/1/credit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.fieldErrors.amount").exists());

    verify(walletService, never())
        .credit(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("credit_whenBlankReference_returns400ValidationError")
  void credit_whenBlankReference_returns400ValidationError() throws Exception {
    String body =
        "{\"amount\":10.00,\"reference\":\"\",\"description\":\"missing reference\"}";

    mockMvc
        .perform(
            post("/api/v1/wallets/1/credit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.fieldErrors.reference").exists());
  }

  @Test
  @DisplayName("credit_whenIdempotencyKeyHeaderMissing_returns400ValidationError")
  void credit_whenIdempotencyKeyHeaderMissing_returns400ValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/wallets/1/credit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("50.00"), "ref", "credit"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.headerErrors['Idempotency-Key']").exists());

    verify(walletService, never())
        .credit(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("credit_whenIdempotencyKeyHeaderBlank_returns400ValidationError")
  void credit_whenIdempotencyKeyHeaderBlank_returns400ValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/wallets/1/credit")
                .header("Idempotency-Key", "")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("50.00"), "ref", "credit"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("credit_whenWalletNotFound_returns404WalletNotFound")
  void credit_whenWalletNotFound_returns404WalletNotFound() throws Exception {
    when(walletService.credit(eq(999L), eq(IDEMPOTENCY_KEY), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new WalletNotFoundException("Wallet not found for playerId: 999"));

    mockMvc
        .perform(
            post("/api/v1/wallets/999/credit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("50.00"), "ref", "credit"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Wallet not found for playerId: 999"));
  }

  @Test
  @DisplayName("debit_whenInsufficientBalance_returns409InsufficientBalance")
  void debit_whenInsufficientBalance_returns409InsufficientBalance() throws Exception {
    when(walletService.debit(eq(1L), eq(IDEMPOTENCY_KEY), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new InsufficientBalanceException("Wallet balance is insufficient for debit operation"));

    mockMvc
        .perform(
            post("/api/v1/wallets/1/debit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("999.00"), "ref", "debit"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
  }

  @Test
  @DisplayName("credit_whenIdempotencyKeyConflict_returns409Conflict")
  void credit_whenIdempotencyKeyConflict_returns409Conflict() throws Exception {
    when(walletService.credit(eq(1L), eq(IDEMPOTENCY_KEY), org.mockito.ArgumentMatchers.any()))
        .thenThrow(
            new IdeIdempotencyKeyConflictException(
                "Idempotency key already used with different parameters"));

    mockMvc
        .perform(
            post("/api/v1/wallets/1/credit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("50.00"), "ref", "credit"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
  }

  @Test
  @DisplayName("credit_whenRequestIsDuplicate_returns409DuplicateRequest")
  void credit_whenRequestIsDuplicate_returns409DuplicateRequest() throws Exception {
    when(walletService.credit(eq(1L), eq(IDEMPOTENCY_KEY), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new DuplicateRequestException("Request is already being processed"));

    mockMvc
        .perform(
            post("/api/v1/wallets/1/credit")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new WalletOperationRequest(new BigDecimal("50.00"), "ref", "credit"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"))
        .andExpect(jsonPath("$.message").value("Request is already being processed"));
  }

  @Test
  @DisplayName("getBalance_whenWalletExists_returns200WithBalance")
  void getBalance_whenWalletExists_returns200WithBalance() throws Exception {
    when(walletService.getBalance(1L)).thenReturn(new WalletBalanceResponse(1L, new BigDecimal("123.45"), "COIN"));

    mockMvc
        .perform(get("/api/v1/wallets/1/balance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.playerId").value(1))
        .andExpect(jsonPath("$.balance").value(123.45))
        .andExpect(jsonPath("$.currency").value("COIN"));
  }

  @Test
  @DisplayName("getBalance_whenWalletNotFound_returns404WalletNotFound")
  void getBalance_whenWalletNotFound_returns404WalletNotFound() throws Exception {
    when(walletService.getBalance(999L))
        .thenThrow(new WalletNotFoundException("Wallet not found for playerId: 999"));

    mockMvc
        .perform(get("/api/v1/wallets/999/balance"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
  }
}
