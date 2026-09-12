CREATE TABLE ledger_transactions
(

    ledger_transaction_id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    request_id VARCHAR(100)
    NOT NULL UNIQUE,
    type VARCHAR(20)
    NOT NULL,
    amount NUMERIC(19,2)
    NOT NULL,
    balance_before NUMERIC(19,2),
    balance_after NUMERIC(19,2),
    reference VARCHAR(100),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_wallet
    FOREIGN KEY(wallet_id)
    REFERENCES wallets(wallet_id)

);