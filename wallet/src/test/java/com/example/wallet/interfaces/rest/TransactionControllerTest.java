package com.example.wallet.interfaces.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.application.dto.TransactionPage;
import com.example.wallet.application.dto.TransactionResponse;
import com.example.wallet.application.service.TransactionQueryService;
import com.example.wallet.domain.exception.WalletNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@DisplayName("TransactionController API Tests")
class TransactionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TransactionQueryService transactionQueryService;

  @Test
  @DisplayName("getTransactions_whenHistoryExists_returns200WithPage")
  void getTransactions_whenHistoryExists_returns200WithPage() throws Exception {
    TransactionPage page =
        new TransactionPage(
            List.of(
                new TransactionResponse(
                    1L,
                    1L,
                    "CREDIT",
                    new BigDecimal("50.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("150.00"),
                    "ref-1",
                    "credit",
                    Instant.parse("2026-01-01T00:00:00Z"))),
            0,
            20,
            1,
            1,
            true,
            true);

    when(transactionQueryService.getTransactions(1L, 0, 20)).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/wallets/1/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].transactionId").value(1))
        .andExpect(jsonPath("$.content[0].type").value("CREDIT"))
        .andExpect(jsonPath("$.content[0].balanceAfter").value(150.00))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  @DisplayName("getTransactions_withCustomPagination_passesParametersThrough")
  void getTransactions_withCustomPagination_passesParametersThrough() throws Exception {
    TransactionPage page =
        new TransactionPage(List.of(), 2, 10, 25, 3, false, false);

    when(transactionQueryService.getTransactions(1L, 2, 10)).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/wallets/1/transactions").param("page", "2").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("getTransactions_whenEmptyHistory_returns200WithEmptyPage")
  void getTransactions_whenEmptyHistory_returns200WithEmptyPage() throws Exception {
    when(transactionQueryService.getTransactions(1L, 0, 20))
        .thenReturn(new TransactionPage(List.of(), 0, 20, 0, 0, true, true));

    mockMvc
        .perform(get("/api/v1/wallets/1/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("getTransactions_whenWalletNotFound_returns404")
  void getTransactions_whenWalletNotFound_returns404() throws Exception {
    when(transactionQueryService.getTransactions(eq(999L), eq(0), eq(20)))
        .thenThrow(new WalletNotFoundException("Wallet not found for playerId: 999"));

    mockMvc
        .perform(get("/api/v1/wallets/999/transactions"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Wallet not found for playerId: 999"));
  }
}