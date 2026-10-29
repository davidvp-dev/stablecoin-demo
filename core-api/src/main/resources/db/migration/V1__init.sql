-- =====================================================================
-- stablecoin-demo · core-api · initial schema
-- Location: core-api/src/main/resources/db/migration/V1__init.sql
-- DO NOT edit once applied: changes go in V2__..., V3__...
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Wallet verification challenges (EIP-191 / SIWE)
-- ---------------------------------------------------------------------
CREATE TABLE wallet_challenges (
    nonce VARCHAR(64) PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL CHECK (
        wallet_address ~ '^0x[0-9a-f]{40}$'
    ),
    message TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_wallet_challenges_expires ON wallet_challenges (expires_at);

-- ---------------------------------------------------------------------
-- 2. Verified wallets (whitelist: tokens are only minted to these addresses)
-- ---------------------------------------------------------------------
CREATE TABLE wallets (
    wallet_address VARCHAR(42) PRIMARY KEY CHECK (
        wallet_address ~ '^0x[0-9a-f]{40}$'
    ),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- 3. Issuance (MINT) and redemption (REDEEM) orders
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    type VARCHAR(10) NOT NULL CHECK (type IN ('MINT', 'REDEEM')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (
        status IN (
            'PENDING',
            'PAID',
            'SUBMITTED',
            'CONFIRMED',
            'FAILED'
        )
    ),
    wallet_address VARCHAR(42) NOT NULL REFERENCES wallets (wallet_address),
    amount_eur NUMERIC(18, 2) NOT NULL CHECK (amount_eur > 0),
    amount_units NUMERIC(78, 0) NOT NULL CHECK (amount_units > 0), -- token base units (uint256)
    tx_hash VARCHAR(66) UNIQUE CHECK (tx_hash ~ '^0x[0-9a-f]{64}$'),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_wallet ON orders (wallet_address);

CREATE INDEX idx_orders_status ON orders (status);

-- ---------------------------------------------------------------------
-- 4. Simulated bank: incoming (MINT payment) and outgoing (REDEEM payout)
-- ---------------------------------------------------------------------
CREATE TABLE bank_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    order_id UUID NOT NULL REFERENCES orders (id),
    direction VARCHAR(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    amount_eur NUMERIC(18, 2) NOT NULL CHECK (amount_eur > 0),
    iban VARCHAR(34) NOT NULL,
    reference VARCHAR(140),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, direction) -- a single payment/payout per order
);

-- ---------------------------------------------------------------------
-- 5. Double-entry ledger (append-only: never UPDATE or DELETE)
--    Invariant: for each order_id, SUM(debit) = SUM(credit)
-- ---------------------------------------------------------------------
CREATE TABLE ledger_entries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    account VARCHAR(30) NOT NULL CHECK (
        account IN (
            'BANK_RESERVE',
            'CUSTOMER_FUNDS',
            'TOKENS_OUTSTANDING'
        )
    ),
    debit NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (debit >= 0),
    credit NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (
            debit > 0
            AND credit = 0
        )
        OR (
            credit > 0
            AND debit = 0
        )
    )
);

CREATE INDEX idx_ledger_order ON ledger_entries (order_id);

CREATE INDEX idx_ledger_account ON ledger_entries (account);

-- ---------------------------------------------------------------------
-- 6. Idempotency for on-chain events received via Kafka
--    (Kafka delivers "at least once": the same event may arrive twice)
-- ---------------------------------------------------------------------
CREATE TABLE processed_events (
    tx_hash VARCHAR(66) NOT NULL CHECK (tx_hash ~ '^0x[0-9a-f]{64}$'),
    log_index INTEGER NOT NULL CHECK (log_index >= 0),
    block_number BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL, -- e.g. 'Transfer', 'Paused'
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tx_hash, log_index)
);