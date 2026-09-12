CREATE TABLE wallets
(

    wallet_id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    balance NUMERIC(19,2)
    NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL

);