package com.example.wallet.integration;

import com.example.wallet.LocalTestcontainersConfig;
import com.example.wallet.application.dto.WalletOperationRequest;
import com.example.wallet.domain.exception.InsufficientBalanceException;
import com.example.wallet.infrastructure.persistence.repository.WalletRepository;
import com.example.wallet.infrastructure.persistence.repository.LedgerTransactionRepository;
import com.example.wallet.domain.model.Wallet;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.example.wallet.application.service.WalletService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LocalTestcontainersConfig.class)
public class WalletConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    private Long playerId = 1L;

    @BeforeEach
    void setUp() {
        ledgerTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        Wallet wallet = new Wallet();
        wallet.setPlayerId(playerId);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCreatedAt(java.time.Instant.now());
        wallet.setUpdatedAt(java.time.Instant.now());
        walletRepository.save(wallet);
    }

    @Test
    void concurrentDebits_neverAllowNegativeBalance() throws InterruptedException {
        // Setup: 100.00 balance
        Wallet wallet = walletRepository.findByPlayerId(playerId).orElseThrow();
        wallet.setBalance(new BigDecimal("100.00"));
        walletRepository.save(wallet);

        int threadCount = 10;
        BigDecimal debitAmount = new BigDecimal("30.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String key = UUID.randomUUID().toString();
            executor.submit(() -> {
                try {
                    latch.await();
                    walletService.debit(playerId, key, new WalletOperationRequest(debitAmount, "ref", "desc"));
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(3);
        assertThat(failureCount.get()).isEqualTo(7);
        assertThat(walletRepository.findByPlayerId(playerId).get().getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void concurrentCredits_allSucceed() throws InterruptedException {
        int threadCount = 20;
        BigDecimal creditAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String key = UUID.randomUUID().toString();
            executor.submit(() -> {
                try {
                    latch.await();
                    walletService.credit(playerId, key, new WalletOperationRequest(creditAmount, "ref", "desc"));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(20);
        assertThat(walletRepository.findByPlayerId(playerId).get().getBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void concurrentSameIdempotencyKey_onlyOneApplied() throws InterruptedException {
        int threadCount = 10;
        BigDecimal amount = new BigDecimal("10.00");
        String sharedKey = "shared-key";
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    walletService.credit(playerId, sharedKey, new WalletOperationRequest(amount, "ref", "desc"));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for 9 threads
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(walletRepository.findByPlayerId(playerId).get().getBalance()).isEqualByComparingTo("10.00");
    }
}
