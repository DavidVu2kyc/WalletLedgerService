package com.example.wallet.infrastructure.persistence.repository;

import com.example.wallet.domain.model.LedgerTransaction;
import com.example.wallet.domain.model.Wallet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

  List<LedgerTransaction> findByWallet(Wallet wallet);

  Optional<LedgerTransaction> findByRequestId(String requestId);

  Page<LedgerTransaction> findByWalletPlayerId(Long playerId, Pageable pageable);
}
