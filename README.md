# Wallet Ledger Backend Service

A Spring Boot service for wallet credits, debits, balances, and transaction history. It maintains a current balance for fast reads and an immutable ledger record for every successful balance change.

## How to run

### Prerequisites

- Java 21
- Docker Desktop (or another Docker-compatible runtime)
- Maven is optional: the included Maven wrapper (`./mvnw`) is used below.

### Project setup and database
Find the root project

```bash
cd wallet
```

From the repository root, start the local PostgreSQL database:

```bash
docker compose up -d postgres
```

The application connects to `localhost:5432/wallet_db` with the development credentials defined in `docker-compose.yaml`. Flyway applies the schema migrations automatically when the application starts. To start the service:

```bash
./mvnw spring-boot:run
```

The service listens on `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

The balance integrity endpoint, `GET /api/v1/wallets/{playerId}/balance/verify`, recomputes a wallet's balance from its ledger without changing any data. It returns the stored balance, the calculated ledger balance, and whether they match.

To stop the local database while retaining its Docker volume:

```bash
docker compose down
```

### Execute the tests

```bash
./mvnw clean verify
```

Integration and concurrency tests start PostgreSQL through Testcontainers (`postgres:16-alpine`), so Docker must be running even when the application database is not started separately. The command also runs the configured JaCoCo coverage checks; its HTML report is written to `target/site/jacoco/index.html`.

## Design decisions

### Ledger approach

The service uses a hybrid, append-only ledger design:

- `ledger_transactions` is the audit trail. A successful credit or debit creates one record containing the request ID, type, amount, balance before/after, reference, description, and timestamp.
- `wallets.balance` is a materialized current balance. It makes balance reads and insufficient-funds checks inexpensive, while the ledger provides the history needed for audit and reconciliation.
- Ledger rows are created within the same database transaction as the balance update, so a balance cannot be committed without its matching ledger entry (or vice versa).

This deliberately favors read performance and straightforward operational checks over calculating the balance by summing every ledger row at request time. The trade-off is that the stored balance is derived state: reconciliation tooling would be useful in production to periodically verify it against the ledger.

The included balance-integrity endpoint provides an on-demand reconciliation check; a scheduled job and alerting would be the next production step.

The database stores monetary amounts as `NUMERIC(19,2)`, and the application uses `BigDecimal`, avoiding floating-point rounding errors.

## Concurrency & Idempotency

Each credit or debit runs in a Spring `@Transactional` transaction. For money-moving operations, the wallet is read using a pessimistic write lock (`SELECT ... FOR UPDATE`), which serializes updates for the same player wallet. The `Wallet` entity also has a JPA `@Version` field as an additional optimistic-locking safeguard. Operations on different wallets are not unnecessarily serialized.

Before changing a balance, the service checks that sufficient funds are available while that wallet lock is held. This prevents two concurrent debits from independently observing the same balance and both spending it.

Clients must send an `Idempotency-Key` header for each credit or debit. The key is persisted as `ledger_transactions.request_id`, which has a database `UNIQUE` constraint. A retry with the same key, amount, operation type, and reference returns the original transaction result without applying the balance change again. Reusing a key with a different payload returns `409 Conflict` (`IDEMPOTENCY_KEY_CONFLICT`). The unique constraint is also the final protection if identical requests race each other across application instances.

## Testing approach

The test suite combines focused unit tests, repository tests, REST/controller tests, and Spring Boot integration tests. Database-backed tests use PostgreSQL via Testcontainers rather than an in-memory substitute, so transaction locking and numeric behavior are exercised against the production database family.

The concurrent-debit test (`WalletConcurrencyTest#concurrentDebits_neverAllowNegativeBalance`) is the key safety case:

1. It seeds one wallet with `100.00`.
2. Ten worker threads are released together using a `CountDownLatch`.
3. Each requests a distinct debit of `30.00`.
4. The test asserts exactly three successful debits, seven insufficient-balance failures, and a final balance of `10.00`.

The same class also verifies that concurrent credits with different keys all succeed, and that concurrent requests sharing one idempotency key produce only one applied credit.

## Assumptions & limitations

- A wallet must already exist; this service does not currently expose wallet-creation or authentication/authorization endpoints.
- The idempotency key is globally unique at the ledger level. Production systems may prefer scoping it by client or wallet, alongside expiration and retention policies.
- The design has no outbox/event publication, reconciliation job, or compensating/reversal workflow. These would be natural additions for integrations, audit operations, and long-running failure recovery.
- PostgreSQL row locks preserve correctness but can increase latency for a very hot single wallet. If that becomes a bottleneck, queueing/partitioning strategies or carefully designed atomic SQL updates should be evaluated without weakening the ledger guarantees.
- Amounts use two decimal places and the balance response currently identifies the currency as `COIN`; multi-currency wallets and currency-specific precision are out of scope.
- Testcontainers requires Docker availability; the local compose database uses the development credentials committed in this repository and should not be used as a production configuration.
