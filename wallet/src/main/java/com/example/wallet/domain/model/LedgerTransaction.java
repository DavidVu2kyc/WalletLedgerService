package com.example.wallet.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
@NoArgsConstructor
public class LedgerTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ledger_transaction_id")
  private Long transactionId;

  @ManyToOne
  @JoinColumn(name = "wallet_id", nullable = false)
  private Wallet wallet;

  @Column(name = "request_id", unique = true, nullable = false, length = 100)
  private String requestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private TransactionType type;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "balance_before", precision = 19, scale = 2)
  private BigDecimal balanceBefore;

  @Column(name = "balance_after", precision = 19, scale = 2)
  private BigDecimal balanceAfter;

  @Column(name = "reference", length = 100)
  private String reference;

  @Column(name = "description", length = 255)
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}