# Wallet Ledger Backend Service

A Spring Boot service for wallet credits, debits, balances, and transaction history. It maintains a current balance for fast reads and an immutable ledger record for every successful balance change.

## How to Run

### Prerequisites

* Java 21
* Docker Desktop (or another Docker-compatible runtime)
* Git
* Maven is optional — the project includes the Maven Wrapper

### 1. Find the Project Root

**macOS / Linux**

```bash
cd wallet
```

**Windows PowerShell**

```powershell
cd wallet
```

If you cloned the repository and are already in the project root, you can skip this step.

---

### 2. Start PostgreSQL

From the repository root:

```bash
docker compose up -d
```

Verify that the database container is running:

```bash
docker compose ps
```

The application connects to:

```text
localhost:5432/wallet_db
```

using the development credentials defined in `docker-compose.yaml`.

Flyway applies the database schema migrations automatically when the application starts.

---

### 3. Start the Application

#### macOS / Linux

Using the Maven Wrapper:

```bash
./mvnw spring-boot:run
```

If the wrapper does not have execute permission:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

#### Windows PowerShell

```powershell
.\mvnw.cmd spring-boot:run
```

#### Alternative: System Maven

If Maven is installed globally:

**macOS / Linux**

```bash
mvn spring-boot:run
```

**Windows PowerShell**

```powershell
mvn spring-boot:run
```

The service listens on:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

---

### 4. Verify Wallet Balance Integrity

The balance-integrity endpoint:

```http
GET /api/v1/wallets/{playerId}/balance/verify
```

recomputes the wallet balance from the immutable ledger without modifying any data.

It returns:

* Stored wallet balance
* Calculated ledger balance
* Whether both balances match

Example:

```http
GET http://localhost:8080/api/v1/wallets/1/balance/verify
```

---

### 5. Stop the Local Database

To stop the PostgreSQL container while retaining its Docker volume:

```bash
docker compose down
```

To stop the containers **and remove their volumes/data**:

```bash
docker compose down -v
```

> Be careful with `-v`: it removes the local database data.

---

# Execute the Tests

The test suite uses Testcontainers for database-backed integration and concurrency tests.

Docker must be running before executing the tests.

### macOS / Linux

```bash
./mvnw clean verify
```

### Windows PowerShell

```powershell
.\mvnw.cmd clean verify
```

### System Maven

```bash
mvn clean verify
```

Testcontainers automatically starts PostgreSQL using:

```text
postgres:16-alpine
```

Therefore, the application's local PostgreSQL container does **not** need to be running separately for the Testcontainers-based tests.

The command also executes the configured JaCoCo coverage checks.

HTML coverage report:

```text
target/site/jacoco/index.html
```

Open the report manually after the build completes.

**macOS**

```bash
open target/site/jacoco/index.html
```

**Windows PowerShell**

```powershell
Start-Process target/site/jacoco/index.html
```

---

# Design Decisions

## Ledger Approach

The service uses a hybrid, append-only ledger design:

* `ledger_transactions` is the audit trail. A successful credit or debit creates one record containing the request ID, type, amount, balance before/after, reference, description, and timestamp.
* `wallets.balance` is a materialized current balance. It makes balance reads and insufficient-funds checks inexpensive, while the ledger provides the history required for audit and reconciliation.
* Ledger rows are created within the same database transaction as the balance update, so a balance cannot be committed without its matching ledger entry, or vice versa.

This deliberately favors read performance and straightforward operational checks over calculating the balance by summing every ledger row at request time.

The trade-off is that the stored balance is derived state. In production, reconciliation tooling should periodically verify the materialized balance against the immutable ledger.

The included balance-integrity endpoint provides an on-demand reconciliation check. A scheduled reconciliation job with alerting would be the natural next production step.

The database stores monetary amounts as:

```sql
NUMERIC(19,2)
```

and the application uses:

```java
BigDecimal
```

This avoids floating-point rounding errors when handling monetary values.

---

# Concurrency & Idempotency

Each credit or debit executes inside a Spring:

```java
@Transactional
```

transaction.

For money-moving operations, the wallet is read using a pessimistic write lock:

```sql
SELECT ... FOR UPDATE
```

This serializes updates for the same player wallet while allowing operations on different wallets to proceed independently.

The `Wallet` entity also uses a JPA:

```java
@Version
```

field as an additional optimistic-locking safeguard.

Before modifying the balance, the service checks whether sufficient funds are available while the wallet lock is held.

This prevents two concurrent debits from independently observing the same balance and both spending the same funds.

## Idempotency

Clients must provide an:

```http
Idempotency-Key
```

header for every credit or debit operation.

The key is persisted as:

```text
ledger_transactions.request_id
```

with a database-level:

```text
UNIQUE
```

constraint.

A retry using the same key, amount, operation type, and reference returns the original transaction result without applying the balance change again.

Reusing an existing key with a different payload returns:

```text
409 Conflict
```

with:

```text
IDEMPOTENCY_KEY_CONFLICT
```

The database unique constraint also provides the final protection if identical requests race across multiple application instances.

---

# Testing Approach

The test suite combines:

* Unit tests
* Repository tests
* REST/controller tests
* Spring Boot integration tests
* PostgreSQL Testcontainers tests
* Concurrency tests

Database-backed tests use PostgreSQL through Testcontainers rather than an in-memory database.

This allows transaction behavior, row-level locking, constraints, and numeric behavior to be exercised against the same database family used by the application.

## Concurrent Debit Test

The key concurrency safety case is:

```text
WalletConcurrencyTest#concurrentDebits_neverAllowNegativeBalance
```

The test:

1. Seeds one wallet with `100.00`.
2. Creates ten worker threads.
3. Releases all workers concurrently using a `CountDownLatch`.
4. Each worker attempts to debit `30.00`.
5. Asserts exactly three successful debits.
6. Asserts seven insufficient-balance failures.
7. Verifies the final balance is `10.00`.

The same test class also verifies:

* Concurrent credits with different idempotency keys all succeed.
* Concurrent requests using the same idempotency key result in only one applied credit.

This provides coverage for both **concurrency control** and **idempotency under race conditions**.

---

# Assumptions & Limitations

* A wallet must already exist. The service does not currently expose wallet creation.
* Authentication and authorization are outside the scope of this service.
* The idempotency key is globally unique at the ledger level. A production implementation may scope keys by client or wallet and introduce expiration/retention policies.
* There is currently no outbox/event-publication mechanism.
* There is no scheduled reconciliation job or alerting mechanism.
* There is no compensating/reversal workflow.
* PostgreSQL row locks preserve correctness but may increase latency for a very hot wallet. If this becomes a bottleneck, queueing, partitioning, or carefully designed atomic SQL updates could be evaluated without weakening the ledger guarantees.
* Amounts currently support two decimal places.
* The balance response identifies the currency as `COIN`.
* Multi-currency wallets and currency-specific precision are out of scope.
* Testcontainers requires Docker to be available.
* The local Docker Compose database uses development credentials committed in the repository and must not be used as production configuration.

---

# Quick Start

## macOS

```bash
cd wallet

docker compose up -d

./mvnw spring-boot:run
```

Run tests:

```bash
./mvnw clean verify
```

Open Swagger:

```bash
open http://localhost:8080/swagger-ui.html
```

Stop PostgreSQL:

```bash
docker compose down
```

---

## Windows PowerShell

```powershell
cd wallet

docker compose up -d

.\mvnw.cmd spring-boot:run
```

Run tests:

```powershell
.\mvnw.cmd clean verify
```

Open Swagger:

```powershell
Start-Process http://localhost:8080/swagger-ui.html
```

Open JaCoCo:

```powershell
Start-Process target/site/jacoco/index.html
```

Stop PostgreSQL:

```powershell
docker compose down
```

---

# Service Endpoints

| Purpose                  | Method | Endpoint                                    |
| ------------------------ | ------ | ------------------------------------------- |
| Swagger UI               | GET    | `/swagger-ui.html`                          |
| Wallet balance           | GET    | `/api/v1/wallets/{playerId}/balance`        |
| Verify balance integrity | GET    | `/api/v1/wallets/{playerId}/balance/verify` |
| Credit wallet            | POST   | `/api/v1/wallets/{playerId}/credit`         |
| Debit wallet             | POST   | `/api/v1/wallets/{playerId}/debit`          |
| Transaction history      | GET    | `/api/v1/wallets/{playerId}/transactions`   |

> The exact endpoint list should be kept synchronized with the controller mappings in the source code.
