package com.example.wallet.infrastructure.persistence.repository;

import com.example.wallet.domain.model.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

  Optional<Wallet> findByPlayerId(Long playerId);

  /**
   * Finds a wallet by player ID with a pessimistic write lock.
   *
   * <p>This is used by money-moving operations (credit/debit) to serialize concurrent
   * operations on the same wallet. Without this lock, concurrent requests would either
   * fail with optimistic-locking exceptions or, worse, lose updates.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.playerId = :playerId")
  Optional<Wallet> findByPlayerIdForUpdate(@Param("playerId") Long playerId);
}