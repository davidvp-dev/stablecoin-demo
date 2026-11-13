-- =====================================================================
-- stablecoin-demo · core-api · initial schema (database core_db)
-- Source of truth: docs/architecture.md (data model and state machines)
-- Conventions:
--   * amounts in integer euro cents (BIGINT), never floating point
--   * wallet addresses stored lowercase (normalized); the API returns EIP-55 checksum
--   * once this migration has been applied outside a local dev DB, changes go in V2__..., V3__...
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Wallets and verification challenges (RF-CORE-01..03)
-- ---------------------------------------------------------------------
CREATE TABLE wallets (
    wallet_address VARCHAR(42) PRIMARY KEY CHECK (wallet_address ~ '^0x[0-9a-f]{40}$'),
    status         VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VERIFIED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at    TIMESTAMPTZ,
    CHECK ((status = 'VERIFIED') = (verified_at IS NOT NULL))
);

CREATE TABLE wallet_challenges (
    nonce          VARCHAR(64) PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL REFERENCES wallets (wallet_address),
    message        TEXT        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_wallet_challenges_wallet ON wallet_challenges (wallet_address);

-- ---------------------------------------------------------------------
-- 2. Mint orders (RF-CORE-04..11) — state machine in docs/architecture.md
-- ---------------------------------------------------------------------
CREATE TABLE mint_orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_address   VARCHAR(42) NOT NULL REFERENCES wallets (wallet_address),
    amount_cents     BIGINT      NOT NULL CHECK (amount_cents > 0),
    sepa_reference   VARCHAR(35) NOT NULL UNIQUE,
    status           VARCHAR(20) NOT NULL DEFAULT 'CREATED' CHECK (
        status IN ('CREATED', 'PAID', 'REJECTED', 'EXPIRED', 'APPROVED', 'SUBMITTED', 'COMPLETED', 'FAILED')
    ),
    idempotency_key  VARCHAR(64) NOT NULL UNIQUE,
    request_hash     CHAR(64)    NOT NULL, -- SHA-256 of the request body, detects key reuse with another body
    tx_hash          VARCHAR(66) UNIQUE CHECK (tx_hash ~ '^0x[0-9a-f]{64}$'),
    failure_reason   TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_mint_orders_wallet ON mint_orders (wallet_address, created_at DESC);
CREATE INDEX idx_mint_orders_expiry ON mint_orders (expires_at) WHERE status = 'CREATED';

-- ---------------------------------------------------------------------
-- 3. Redeem orders (RF-CORE-12..15)
-- ---------------------------------------------------------------------
CREATE TABLE redeem_orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    onchain_id       CHAR(66)    NOT NULL UNIQUE CHECK (onchain_id ~ '^0x[0-9a-f]{64}$'), -- bytes32, see docs/events.md
    wallet_address   VARCHAR(42) NOT NULL REFERENCES wallets (wallet_address),
    amount_cents     BIGINT      NOT NULL CHECK (amount_cents > 0),
    destination_iban VARCHAR(34) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'CREATED' CHECK (
        status IN ('CREATED', 'TOKENS_BURNED', 'UNDER_REVIEW', 'EXPIRED', 'COMPLETED')
    ),
    idempotency_key  VARCHAR(64) NOT NULL UNIQUE,
    request_hash     CHAR(64)    NOT NULL,
    tx_hash          VARCHAR(66) UNIQUE CHECK (tx_hash ~ '^0x[0-9a-f]{64}$'),
    failure_reason   TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_redeem_orders_wallet ON redeem_orders (wallet_address, created_at DESC);
CREATE INDEX idx_redeem_orders_expiry ON redeem_orders (expires_at) WHERE status = 'CREATED';

-- ---------------------------------------------------------------------
-- 4. State transitions of every order (RF-CORE-20, 22)
--    Rejected transitions are stored too (applied = false) for traceability.
-- ---------------------------------------------------------------------
CREATE TABLE order_state_transitions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_type  VARCHAR(6)  NOT NULL CHECK (order_type IN ('MINT', 'REDEEM')),
    order_id    UUID        NOT NULL,
    from_status VARCHAR(20),          -- NULL for the initial transition
    to_status   VARCHAR(20) NOT NULL,
    cause       VARCHAR(100) NOT NULL, -- e.g. 'IncomingTransfer', 'MintConfirmed', 'ExpiryJob'
    event_id    UUID,                  -- Kafka eventId when the cause is an event
    applied     BOOLEAN     NOT NULL DEFAULT TRUE,
    at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transitions_order ON order_state_transitions (order_type, order_id, at);

-- ---------------------------------------------------------------------
-- 5. Simulated bank transfers (RF-CORE-06..08, 13, 14)
--    Every incoming transfer is stored, assigned to an order or not.
-- ---------------------------------------------------------------------
CREATE TABLE bank_transfers (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    direction         VARCHAR(8)   NOT NULL CHECK (direction IN ('INCOMING', 'OUTGOING')),
    reference         VARCHAR(140) NOT NULL,
    amount_cents      BIGINT       NOT NULL CHECK (amount_cents > 0),
    counterparty_iban VARCHAR(34),
    outcome           VARCHAR(10)  NOT NULL CHECK (outcome IN ('MATCHED', 'REJECTED', 'UNASSIGNED', 'REFUND', 'PAYOUT')),
    mint_order_id     UUID REFERENCES mint_orders (id),
    redeem_order_id   UUID REFERENCES redeem_orders (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (mint_order_id IS NULL OR redeem_order_id IS NULL),
    CHECK ((outcome = 'UNASSIGNED') = (mint_order_id IS NULL AND redeem_order_id IS NULL)),
    UNIQUE (mint_order_id, direction),   -- one payment (and at most one refund) per mint order
    UNIQUE (redeem_order_id, direction)  -- one payout per redeem order
);

CREATE INDEX idx_bank_transfers_reference ON bank_transfers (reference);

-- ---------------------------------------------------------------------
-- 6. Double-entry ledger (RF-CORE-16): append-only, two accounts
--    Mint completed: debit BANK_RESERVE, credit EURD_CIRCULATION. Redeem: the reverse.
-- ---------------------------------------------------------------------
CREATE TABLE ledger_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mint_order_id   UUID UNIQUE REFERENCES mint_orders (id),
    redeem_order_id UUID UNIQUE REFERENCES redeem_orders (id),
    debit_account   VARCHAR(20) NOT NULL CHECK (debit_account IN ('BANK_RESERVE', 'EURD_CIRCULATION')),
    credit_account  VARCHAR(20) NOT NULL CHECK (credit_account IN ('BANK_RESERVE', 'EURD_CIRCULATION')),
    amount_cents    BIGINT      NOT NULL CHECK (amount_cents > 0),
    booked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (debit_account <> credit_account),
    CHECK ((mint_order_id IS NULL) <> (redeem_order_id IS NULL))
);

CREATE FUNCTION forbid_ledger_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_changes();

-- ---------------------------------------------------------------------
-- 7. Transactional outbox (RF-CORE-10): written in the same transaction
--    as the state change, published to Kafka by a background process.
-- ---------------------------------------------------------------------
CREATE TABLE outbox_events (
    event_id     UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100) NOT NULL, -- order id
    event_type   VARCHAR(50)  NOT NULL,
    payload      JSONB        NOT NULL, -- full event envelope, see docs/events.md
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- ---------------------------------------------------------------------
-- 8. Idempotent consumers (RF-CORE-21): each eventId is processed once
-- ---------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
