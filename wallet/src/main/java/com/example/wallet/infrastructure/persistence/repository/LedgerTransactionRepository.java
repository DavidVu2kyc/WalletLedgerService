package com.example.wallet.infrastructure.persistence.repository;

import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.domain.model.TransactionType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

  Optional<LedgerTransaction> findByRequestId(String requestId);

  Page<LedgerTransaction> findByWalletPlayerId(Long playerId, Pageable pageable);

  @Query(
      "select coalesce(sum(l.amount), 0) "
          + "from LedgerTransaction l "
          + "where l.wallet.playerId = :playerId and l.type = :type")
  BigDecimal sumAmountByPlayerIdAndType(
      @Param("playerId") Long playerId, @Param("type") TransactionType type);
}
