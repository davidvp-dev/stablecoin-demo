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

	public static final String INSERT_MINT_ORDER_STATE_TRANSITION_QUERY = """
			INSERT INTO order_state_transitions (order_id, order_type, from_status, to_status, cause)
			VALUES (:orderId, :orderType, :fromStatus, :toStatus, :cause)
			""";

	private Constants() {
	}
}
