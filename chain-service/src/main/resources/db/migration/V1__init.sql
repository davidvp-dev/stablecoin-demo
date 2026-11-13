-- =====================================================================
-- stablecoin-demo · chain-service · initial schema (database chain_db)
-- chain-service knows nothing about customers or euros: only orders to
-- execute on-chain and the events it observes.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Mint transactions sent by chain-service (RF-CHAIN-01, 04..06)
--    One row per order id: a redelivered MintApproved never sends a second tx.
-- ---------------------------------------------------------------------
CREATE TABLE sent_transactions (
    order_id       UUID PRIMARY KEY,
    onchain_id     CHAR(66)      NOT NULL UNIQUE CHECK (onchain_id ~ '^0x[0-9a-f]{64}$'),
    chain_id       BIGINT        NOT NULL,
    from_address   VARCHAR(42)   NOT NULL CHECK (from_address ~ '^0x[0-9a-f]{40}$'),
    to_address     VARCHAR(42)   NOT NULL CHECK (to_address ~ '^0x[0-9a-f]{40}$'), -- mint recipient
    amount_units   NUMERIC(78, 0) NOT NULL CHECK (amount_units > 0),           -- cents x 10,000
    nonce          BIGINT,
    tx_hash        VARCHAR(66)   UNIQUE CHECK (tx_hash ~ '^0x[0-9a-f]{64}$'),
    status         VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'SUBMITTED', 'CONFIRMED', 'FAILED')
    ),
    attempts       INTEGER       NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    failure_reason TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (chain_id, from_address, nonce)
);

CREATE INDEX idx_sent_transactions_status ON sent_transactions (status);

-- ---------------------------------------------------------------------
-- 2. Event-reading checkpoint (RF-CHAIN-07): last fully processed block
-- ---------------------------------------------------------------------
CREATE TABLE block_checkpoints (
    chain_id             BIGINT      NOT NULL,
    contract_address     VARCHAR(42) NOT NULL CHECK (contract_address ~ '^0x[0-9a-f]{40}$'),
    last_processed_block BIGINT      NOT NULL CHECK (last_processed_block >= 0),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chain_id, contract_address)
);

-- ---------------------------------------------------------------------
-- 3. Contract events observed and waiting for N confirmations (RF-CHAIN-07..09)
--    The eventId is derived deterministically from (chainId, txHash, logIndex)
--    so a republish after a crash carries the same eventId (see docs/events.md).
-- ---------------------------------------------------------------------
CREATE TABLE observed_events (
    chain_id     BIGINT      NOT NULL,
    tx_hash      VARCHAR(66) NOT NULL CHECK (tx_hash ~ '^0x[0-9a-f]{64}$'),
    log_index    INTEGER     NOT NULL CHECK (log_index >= 0),
    block_number BIGINT      NOT NULL,
    block_hash   VARCHAR(66) NOT NULL CHECK (block_hash ~ '^0x[0-9a-f]{64}$'),
    event_name   VARCHAR(30) NOT NULL CHECK (event_name IN ('TokensEmitted', 'TokensRedeemed')),
    event_id     UUID        NOT NULL UNIQUE,
    order_id     UUID        NOT NULL,
    payload      JSONB       NOT NULL,
    observed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    PRIMARY KEY (chain_id, tx_hash, log_index)
);

CREATE INDEX idx_observed_events_pending ON observed_events (block_number) WHERE published_at IS NULL;
