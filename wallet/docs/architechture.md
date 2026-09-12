# Architecture Decision Records (ADR)

## ADR 001: Append-Only Ledger Design
**Status**: Accepted

### Context
We need to maintain a wallet system where balance changes are traceable and auditable. Simply updating a `balance` column in a `wallets` table is insufficient for financial systems as it loses the "why" and "when" of every change.

### Decision
Implement a dual-storage approach:
1. **State Table (`wallets`)**: Stores the current balance for fast lookups.
2. **Ledger Table (`ledger_transactions`)**: An append-only record of every single balance change.

### Consequences
- **Pros**: Full auditability. Ability to reconstruct balance from history.
- **Cons**: Increased storage usage; requires strict transactional integrity to keep state and ledger in sync.

---

## ADR 002: Hybrid Locking Strategy for Concurrency
**Status**: Accepted

### Context
In a gaming environment, players might trigger multiple events (e.g., claiming a reward while making a purchase) that affect their wallet simultaneously. We must prevent "lost updates" and "double spending."

### Decision
Use a combination of **Pessimistic Locking** and **Optimistic Locking**.

1. **Pessimistic Lock (`SELECT FOR UPDATE`)**: Applied at the start of every `credit` or `debit` operation. This ensures that for any specific `playerId`, only one transaction is processed at a time.
2. **Optimistic Lock (`@Version`)**: Applied to the `Wallet` entity. This serves as a safety net for any operation that might not use the pessimistic lock.

### Rationale
Pessimistic locking is preferred here because money-moving operations are high-contention on a *per-user* basis. Optimistic locking alone would lead to many `OptimisticLockException` retries, which degrade user experience and increase latency.

---

## ADR 003: Idempotency via Request ID
**Status**: Accepted

### Context
Network failures can cause clients to retry requests. We must ensure that retrying a `credit` or `debit` operation does not result in the player receiving funds twice.

### Decision
Implement an `Idempotency-Key` (Request ID) pattern.
- The client must provide a unique key for every intent.
- The server stores this key in the `ledger_transactions` table with a `UNIQUE` constraint.
- The service checks for the existence of the key before processing.

### Consequences
- **Pros**: Guarantees "exactly-once" processing.
- **Cons**: Requires the client to manage and persist idempotency keys.

---

## ADR 004: Domain-Driven Design (DDD) Lite
**Status**: Accepted

### Context
The system should be maintainable and decouple business rules (like "cannot debit more than balance") from infrastructure (like "save to Postgres").

### Decision
Organize the code into layers:
- **Domain**: Pure business logic (`Wallet`, `LedgerTransaction`).
- **Application**: Orchestration and DTOs (`WalletService`).
- **Infrastructure**: Persistence and Configuration (`WalletRepository`).
- **Interfaces**: REST Controllers and Error Handling.

### Rationale
This prevents the "Anemic Domain Model" anti-pattern by putting validation logic (e.g., `wallet.debit()`) inside the entity itself.
