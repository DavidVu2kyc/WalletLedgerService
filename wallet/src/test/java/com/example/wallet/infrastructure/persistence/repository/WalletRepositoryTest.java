 package com.example.wallet.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.*;

import com.example.wallet.LocalTestcontainersConfig;
import com.example.wallet.domain.model.Wallet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// ĐÚNG (Spring Boot 3.3.4)
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;  // ← ĐÚNG
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LocalTestcontainersConfig.class)
@DisplayName("WalletRepository Integration Tests")
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",                // ← Hibernate chỉ validate
        "spring.flyway.enabled=true",                            // ← Flyway chạy
        "spring.flyway.locations=classpath:db/migration",        // ← đường dẫn scripts
})
class WalletRepositoryTest {

  @Autowired private WalletRepository walletRepository;

  @Autowired private LedgerTransactionRepository ledgerTransactionRepository;

  @BeforeEach
  void setUp() {
    ledgerTransactionRepository.deleteAll();
    ledgerTransactionRepository.flush();
    walletRepository.deleteAll();
    walletRepository.flush();
  }

  @Test
  @DisplayName("findByPlayerId_whenExists_returnsWallet")
  void testFindByPlayerId_Exists() {
    // Given
    Wallet wallet = createAndSaveWallet(1L, new BigDecimal("100.00"));

    // When
    Optional<Wallet> result = walletRepository.findByPlayerId(1L);

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getPlayerId()).isEqualTo(1L);
    assertThat(result.get().getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName("findByPlayerId_whenNotExists_returnsEmpty")
  void testFindByPlayerId_NotExists() {
    // When
    Optional<Wallet> result = walletRepository.findByPlayerId(999L);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("save_createsNewWallet_withCorrectFields")
  void testSave_CreatesNewWallet() {
    // Given
    Wallet wallet = new Wallet();
    wallet.setPlayerId(2L);
    wallet.setBalance(new BigDecimal("250.50"));
    wallet.setVersion(0L);
    wallet.setCreatedAt(Instant.now());
    wallet.setUpdatedAt(Instant.now());

    // When
    Wallet saved = walletRepository.save(wallet);

    // Then
    assertThat(saved.getWalletId()).isNotNull();
    assertThat(saved.getPlayerId()).isEqualTo(2L);
    assertThat(saved.getBalance()).isEqualByComparingTo(new BigDecimal("250.50"));
    assertThat(saved.getVersion()).isNotNull();

    // Verify persistence
    Optional<Wallet> retrieved = walletRepository.findByPlayerId(2L);
    assertThat(retrieved).isPresent();
  }

  @Test
  @DisplayName("save_withOptimisticLocking_incrementsVersion")
  void  testSave_IncrementsVersion() {
    // Given
    Wallet wallet = createAndSaveWallet(3L, new BigDecimal("100.00"));
    Long originalVersion = wallet.getVersion();

    // When
    wallet.setBalance(new BigDecimal("150.00"));
    wallet.setUpdatedAt(Instant.now());
    Wallet updated = walletRepository.saveAndFlush(wallet);

    // Then
    assertThat(updated.getVersion()).isGreaterThan(originalVersion);
  }

  @Test
  @DisplayName("findByPlayerId_uniqueConstraint_ensuresOneWalletPerPlayer")
  void testFindByPlayerId_UniqueConstraint() {
    // Given
    Long playerId = 4L;
    createAndSaveWallet(playerId, new BigDecimal("100.00"));

    // When & Then - trying to create another wallet for same player should fail
    Wallet duplicate = new Wallet();
    duplicate.setPlayerId(playerId);
    duplicate.setBalance(new BigDecimal("200.00"));
    duplicate.setVersion(0L);
    duplicate.setCreatedAt(Instant.now());
    duplicate.setUpdatedAt(Instant.now());

    assertThatThrownBy(() -> walletRepository.save(duplicate))
        .isNotNull(); // Constraint violation
  }

  @Test
  @DisplayName("save_multipleWallets_persistsIndependently")
  void testSave_MultipleWallets() {
    // Given
    createAndSaveWallet(5L, new BigDecimal("100.00"));
    createAndSaveWallet(6L, new BigDecimal("200.00"));
    createAndSaveWallet(7L, new BigDecimal("300.00"));

    // When
    Optional<Wallet> wallet5 = walletRepository.findByPlayerId(5L);
    Optional<Wallet> wallet6 = walletRepository.findByPlayerId(6L);
    Optional<Wallet> wallet7 = walletRepository.findByPlayerId(7L);

    // Then
    assertThat(wallet5).isPresent();
    assertThat(wallet6).isPresent();
    assertThat(wallet7).isPresent();
    assertThat(wallet5.get().getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(wallet6.get().getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
    assertThat(wallet7.get().getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
  }

  // Helper method
  private Wallet createAndSaveWallet(Long playerId, BigDecimal balance) {
    Wallet wallet = new Wallet();
    wallet.setPlayerId(playerId);
    wallet.setBalance(balance);
    wallet.setVersion(0L);
    wallet.setCreatedAt(Instant.now());
    wallet.setUpdatedAt(Instant.now());
    return walletRepository.save(wallet);
  }
}
