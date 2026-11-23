package com.stablecoin.coreapi.util;

public final class Constants {

	/**
	 * Registers a wallet as PENDING the first time it asks for a challenge; no-op
	 * if it already exists.
	 */
	public static final String UPSERT_PENDING_WALLET_QUERY = """
			INSERT INTO wallets (wallet_address)
			VALUES (:walletAddress)
			ON CONFLICT (wallet_address) DO NOTHING
			""";

	/**
	 * Inserts a new wallet challenge. Used to generate a challenge for a wallet to
	 * sign. The nonce is unique and
	 * single-use, and the challenge expires after a certain time.
	 * The challenge is stored in the database for later verification when the
	 * wallet signs it.
	 */
	public static final String INSERT_WALLET_CHALLENGE_QUERY = """
			INSERT INTO wallet_challenges (nonce, wallet_address, message, expires_at)
			VALUES (:nonce, :walletAddress, :message, :expiresAt)
			""";

	/**
	 * Selects a wallet challenge by its nonce and wallet address, whether it is
	 * used or expired, so the service can tell those cases apart and return a
	 * specific error. This is used to verify a challenge when a wallet signs it.
	 */
	public static final String SELECT_WALLET_CHALLENGE_BY_NONCE_QUERY = """
			SELECT nonce, wallet_address, message, expires_at, used_at, created_at
			FROM wallet_challenges
			WHERE nonce = :nonce AND wallet_address = :walletAddress
			""";

	/**
	 * Consumes a challenge. The {@code used_at IS NULL} guard makes it single-use:
	 * if two requests race with the same nonce, only one of them updates a row.
	 */
	public static final String MARK_WALLET_CHALLENGE_USED_QUERY = """
			UPDATE wallet_challenges
			SET used_at = NOW()
			WHERE nonce = :nonce AND wallet_address = :walletAddress AND expires_at > NOW() AND used_at IS NULL
			""";

	/**
	 * Marks a wallet VERIFIED. Re-verifying an already verified wallet keeps its
	 * original verified_at.
	 */
	public static final String MARK_WALLET_VERIFIED_QUERY = """
			UPDATE wallets
			SET status = 'VERIFIED', verified_at = COALESCE(verified_at, NOW())
			WHERE wallet_address = :walletAddress
			RETURNING verified_at
			""";

	public static final String SELECT_WALLET_BY_ADDRESS_QUERY = """
			SELECT wallet_address, status, verified_at
			FROM wallets
			WHERE wallet_address = :walletAddress
			""";

	public static final String SELECT_MINT_ORDER_BY_IDEMPOTENCY_KEY_QUERY = """
			SELECT id, wallet_address, amount_cents, sepa_reference, status, idempotency_key,
			       request_hash, tx_hash, failure_reason, created_at, updated_at, expires_at
			FROM mint_orders
			WHERE idempotency_key = :idempotencyKey
			""";

	public static final String SIWE_MESSAGE_TEMPLATE = """
			{domain} wants you to sign in with your Ethereum account:
			{address}

			Verify wallet ownership for {appName}.

			URI: {uri}
			Version: 1
			Chain ID: {chainId}
			Nonce: {nonce}
			Issued At: {issuedAt}
			Expiration Time: {expiresAt}""";

	/**
	 * Inserts a mint order. {@code ON CONFLICT DO NOTHING} handles two requests
	 * with the same new Idempotency-Key at the same time: PostgreSQL makes the
	 * second insert wait for the first transaction, and then inserts nothing and
	 * returns no row instead of failing with a unique violation (which would abort
	 * the transaction). The caller then re-reads the order as a replay.
	 */
	public static final String INSERT_MINT_ORDER_QUERY = """
			INSERT INTO mint_orders (wallet_address, amount_cents, sepa_reference, idempotency_key,
			                         request_hash, expires_at)
			VALUES (:walletAddress, :amountCents, :sepaReference, :idempotencyKey,
			        :requestHash, :expiresAt)
			ON CONFLICT (idempotency_key) DO NOTHING
			RETURNING id, status, created_at, updated_at
			""";

	public static final String SELECT_MINT_ORDER_BY_ID_QUERY = """
			SELECT id, wallet_address, amount_cents, sepa_reference, status, idempotency_key,
			       request_hash, tx_hash, failure_reason, created_at, updated_at, expires_at
			FROM mint_orders
			WHERE id = :id
			""";

	/**
	 * Locks the mint order with this SEPA reference until the transaction ends.
	 * Two transfers with the same reference at the same time are then processed
	 * one after the other: the second one sees the status the first one left.
	 */
	public static final String SELECT_MINT_ORDER_BY_SEPA_REFERENCE_FOR_UPDATE_QUERY = """
			SELECT id, wallet_address, amount_cents, sepa_reference, status, idempotency_key,
			       request_hash, tx_hash, failure_reason, created_at, updated_at, expires_at
			FROM mint_orders
			WHERE sepa_reference = :sepaReference
			FOR UPDATE
			""";

	/**
	 * Changes the status only if the order is still in the expected one, so a
	 * stale read can never overwrite a newer status. failure_reason is kept when
	 * the new value is null.
	 */
	public static final String UPDATE_MINT_ORDER_STATUS_QUERY = """
			UPDATE mint_orders
			SET status = :toStatus,
			    failure_reason = COALESCE(CAST(:failureReason AS TEXT), failure_reason),
			    updated_at = now()
			WHERE id = :id AND status = :fromStatus
			""";

	public static final String INSERT_MINT_ORDER_STATE_TRANSITION_QUERY = """
			INSERT INTO order_state_transitions (order_id, order_type, from_status, to_status, cause, applied)
			VALUES (:orderId, :orderType, :fromStatus, :toStatus, :cause, :applied)
			""";

	public static final String INSERT_BANK_TRANSFER_QUERY = """
			INSERT INTO bank_transfers (direction, reference, amount_cents, counterparty_iban, outcome, mint_order_id)
			VALUES (:direction, :reference, :amountCents, :counterpartyIban, :outcome, :mintOrderId)
			RETURNING id, created_at
			""";

	/**
	 * The payload is the full event envelope (docs/events.md) as JSON text; the
	 * outbox publisher sends it to Kafka as is.
	 */
	public static final String INSERT_OUTBOX_EVENT_QUERY = """
			INSERT INTO outbox_events (event_id, topic, message_key, event_type, payload)
			VALUES (:eventId, :topic, :messageKey, :eventType, CAST(:payload AS JSONB))
			""";

	/**
	 * Oldest unpublished events first. FOR UPDATE SKIP LOCKED lets two core-api
	 * instances run the publisher at the same time without sending the same row
	 * twice: each one takes the rows the other hasn't locked.
	 */
	public static final String SELECT_PENDING_OUTBOX_EVENTS_QUERY = """
			SELECT event_id, topic, message_key, event_type, payload::text AS payload
			FROM outbox_events
			WHERE published_at IS NULL
			ORDER BY created_at, event_id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""";

	public static final String MARK_OUTBOX_EVENT_PUBLISHED_QUERY = """
			UPDATE outbox_events
			SET published_at = now()
			WHERE event_id = :eventId
			""";

	/**
	 * CREATED mint orders whose expiry has passed, locked for the expiry job.
	 * SKIP LOCKED leaves out an order the bank simulator is paying right now; if
	 * the payment fails, the next run picks it up.
	 */
	public static final String SELECT_EXPIRED_CREATED_MINT_ORDERS_FOR_UPDATE_QUERY = """
			SELECT id, wallet_address, amount_cents, sepa_reference, status, idempotency_key,
			       request_hash, tx_hash, failure_reason, created_at, updated_at, expires_at
			FROM mint_orders
			WHERE status = 'CREATED' AND expires_at <= now()
			ORDER BY expires_at
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""";

	/** Kafka topic for the events core-api produces about mint orders. */
	public static final String MINT_ORDERS_TOPIC = "mint-orders.v1";

	/** Kafka topic for the events chain-service produces (consumed on Day 4). */
	public static final String CHAIN_EVENTS_TOPIC = "chain-events.v1";

	/**
	 * Status history of one order, oldest first. Rejected transitions
	 * (applied = false) are kept for traceability but are not part of the
	 * order's history. id breaks ties between transitions in the same instant.
	 */
	public static final String SELECT_ORDER_STATE_TRANSITIONS_QUERY = """
			SELECT from_status, to_status, cause, at
			FROM order_state_transitions
			WHERE order_type = :orderType AND order_id = :orderId AND applied = TRUE
			ORDER BY at, id
			""";

	private Constants() {
	}
}
