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

## 📈 Testing Approach

- **Unit Tests**: Validating domain logic in `Wallet` and `LedgerFactory`.
- **Repository Tests**: Verifying SQL queries and locking behavior using Testcontainers (PostgreSQL).
- **Integration Tests**: End-to-end flow testing via `WalletControllerIntegrationTest`.
- **Concurrency Tests**: `WalletConcurrencyTest` uses a `CountDownLatch` and `ExecutorService` to fire dozens of simultaneous requests against a single wallet to ensure the final balance is mathematically correct.
- **Coverage**: Targeted 99% coverage for domain models and services.
