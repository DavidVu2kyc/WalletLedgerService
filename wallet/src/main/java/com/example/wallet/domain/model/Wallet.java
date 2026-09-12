package com.example.wallet.domain.model;

import com.example.wallet.domain.exception.InsufficientBalanceException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wallet_id")
  private Long walletId;

  @Column(name = "player_id", unique = true, nullable = false)
  private Long playerId;

  @Column(name = "balance", precision = 19, scale = 2, nullable = false)
  private BigDecimal balance;

  @Version
  @Column(name = "version")
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public void credit(BigDecimal amount) {
    validate(amount);
    balance = balance.add(amount);
  }

  public void debit(BigDecimal amount) {
    validate(amount);
    if (balance.compareTo(amount) < 0) {
      throw new InsufficientBalanceException();
    }
    balance = balance.subtract(amount);
  }

  private void validate(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
  }
}