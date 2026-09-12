-- Seed test wallets and ledger data for manual controller / API testing.
-- Idempotent: safe to run against an existing database via:
--   TRUNCATE ledger_transactions, wallets RESTART IDENTITY CASCADE;

-- Player 1: healthy wallet with ledger history (balance = 100.00)
INSERT INTO wallets (player_id, balance, version, created_at, updated_at)
SELECT 1, 100.00, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE player_id = 1);

-- Player 2: healthy wallet, different balance (250.50)
INSERT INTO wallets (player_id, balance, version, created_at, updated_at)
SELECT 2, 250.50, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE player_id = 2);

-- Player 3: zero-balance wallet, good for debit / insufficient-funds tests
INSERT INTO wallets (player_id, balance, version, created_at, updated_at)
SELECT 3, 0.00, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE player_id = 3);

-- Ledger history for player 1 (kept arithmetically consistent with balance 100.00)
INSERT INTO ledger_transactions (wallet_id, request_id, type, amount, balance_before, balance_after, reference, description, created_at)
SELECT w.wallet_id, 'seed-credit-1', 'CREDIT', 150.00, 0.00, 150.00, 'seed-init', 'Initial seed credit', NOW() - INTERVAL '3 days'
FROM wallets w
WHERE w.player_id = 1;

INSERT INTO ledger_transactions (wallet_id, request_id, type, amount, balance_before, balance_after, reference, description, created_at)
SELECT w.wallet_id, 'seed-debit-1', 'DEBIT', 50.00, 150.00, 100.00, 'seed-purchase', 'Seed sample debit', NOW() - INTERVAL '1 day'
FROM wallets w
WHERE w.player_id = 1;