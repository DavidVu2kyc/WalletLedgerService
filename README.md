# Wallet Ledger Backend Service

A high-performance, idempotent, and thread-safe wallet system designed to handle player balances and transaction history with a permanent audit trail (ledger).

## 🚀 Getting Started

### Prerequisites
- **Java 21** (Required)
- **Maven 3.9+**
- **Docker & Docker Compose** (for PostgreSQL)

### Quick Start
1. **Start the Database:**
   ```bash
   cd wallet
   docker-compose up -d
   ```
2. **Run the Application:**
   ```bash
   ./mvnw spring-boot:run
   ```
3. **Run Tests:**
   ```bash
   ./mvnw clean verify
   ```
   *This will run all unit, integration, and concurrency tests, and generate a JaCoCo coverage report.*

### API Documentation
Once the app is running, you can access the Swagger UI at:
`http://localhost:8080/swagger-ui/index.html`

---

## 🛠 Technical Architecture

### Core Principles
- **Append-Only Ledger**: Balances are not just numbers in a table; every change is recorded as a `LedgerTransaction`. This ensures a complete audit trail.
- **Strong Consistency**: Uses database-level locking to prevent double-spending and race conditions.
- **Idempotency**: Every write operation requires an `Idempotency-Key` to prevent accidental duplicate transactions.

### API Endpoints
| Method | Endpoint | Description | Key Requirement |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/wallets/{playerId}/credit` | Add funds to wallet | `Idempotency-Key` header |
| `POST` | `/api/v1/wallets/{playerId}/debit` | Subtract funds from wallet | `Idempotency-Key` header |
| `GET` | `/api/v1/wallets/{playerId}/balance` | Get current balance | - |
| `GET` | `/api/v1/wallets/{playerId}/audit` | Reconciled ledger-vs-balance audit | - |
| `GET` | `/api/v1/wallets/{playerId}/transactions` | Paginated history | `page`, `size` params |

---

## 🛡 Safety & Correctness

### 1. Concurrency Control
To handle high-concurrency environments (e.g., a player claiming multiple rewards simultaneously), we use a **Defense-in-Depth** locking strategy:
- **Pessimistic Locking**: The service uses `SELECT ... FOR UPDATE` (via `@Lock(LockModeType.PESSIMISTIC_WRITE)`) when fetching a wallet for a credit/debit operation. This serializes requests for the *same wallet* at the database level.
- **Optimistic Locking**: The `Wallet` entity includes a `@Version` field. If a race condition bypasses the pessimistic lock, JPA will throw an `OptimisticLockException`, preventing data corruption.

### 2. Idempotency Guarantee
To prevent the "double-submit" problem:
- Every transaction is stored with a `request_id` (the `Idempotency-Key` provided by the client).
- The database enforces a `UNIQUE` constraint on `ledger_transactions.request_id`.
- If a duplicate key is submitted:
    - If the payload (amount/reference) matches the original $\rightarrow$ Return the original successful response.
    - If the payload differs $\rightarrow$ Return `409 Conflict` (`IDEMPOTENCY_KEY_CONFLICT`).

### 3. Atomic Transactions
All money-moving operations are wrapped in `@Transactional`. The sequence is:
1. Lock Wallet $\rightarrow$ 2. Update Balance $\rightarrow$ 3. Insert Ledger Record.
If any step fails (e.g., DB crash or constraint violation), the entire operation rolls back.

---

## 📈 Testing Strategy

The test suite is layered to prove both *functional correctness* and *financial integrity*:

- **Unit Tests**: Validate domain logic in `Wallet` and `LedgerFactory` (credit/debit invariants, precision handling).
- **Repository Tests**: Verify SQL queries and locking behavior against a **real PostgreSQL** instance via Testcontainers.
- **Integration Tests**: `WalletControllerIntegrationTest` exercises the full HTTP layer (request validation, headers, error mapping to `400/404/409`).
- **Concurrency Tests**: `WalletConcurrencyTest` is the crown jewel — it proves the locking is not theoretical. It uses a `CountDownLatch` (an `ExecutorService` of many threads each `await()` on the shared start gate so that **all threads race simultaneously** against a single wallet) to fire dozens of concurrent credits/debits. Because every credit/debit acquires the `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) lock on the same wallet row, the operations serialize at the database level and the **final balance is asserted to be mathematically correct** (e.g., `initial + Σ credits - Σ debits`). This test *fails* if the lock were removed, making it a regression guard for balance corruption.
- **Balance Audit**: `auditBalance` reconciles the authoritative `LedgerTransaction` history (CREDIT +, DEBIT −) against the current wallet balance and returns `isConsistent`. Any drift is immediately visible — the ledger is the **source of truth**, not the denormalized wallet balance.
- **Coverage**: Targeted ≥99% on domain models and application services, enforced by JaCoCo during `verify`.

---

## 🧭 Known Limitations & Future Roadmap

The system is production-grade for a single-region, single-PostgreSQL deployment, but the following limitations are acknowledged with a concrete evolution path:

### 1. Distributed Locking (Redis / Redlock)
- **Limitation**: `PESSIMISTIC_WRITE` relies on the single PostgreSQL instance being the coordination point. It does not span multiple database regions and can become a contention bottleneck at extreme volumes.
- **Roadmap**: Introduce a distributed lock (e.g., Redis or the Redlock algorithm) acquired **before** the DB transaction, so the wallet row lock is held for the minimum required time. This allows multi-region horizontal scaling while keeping the DB lock as a final safety net.

### 2. Domain Events (Async Notifications)
- **Limitation**: `WalletService` currently writes the ledger and returns a synchronous response; there is no mechanism for other systems (e.g., notification services, anti-fraud, analytics) to react to wallet events.
- **Roadmap**: Publish domain events (credit/debit succeeded, insufficient funds) via Spring's `ApplicationEventPublisher`, persisted in an outbox table and delivered through a transactional outbox pattern to ensure reliable, exactly-once async processing.

### 3. Multi-Currency Support
- **Limitation**: The wallet is single-currency (`COIN`); no exchange-rate handling or per-currency balance tracking exists.
- **Roadmap**: Model balances with ISO 4217 currency codes, introduce an exchange-rate service (with mid-rate + spread + timestamped FX snapshots), and store the applied conversion on each `LedgerTransaction` so the audit trail remains fully reproducible in a multi-currency world.
