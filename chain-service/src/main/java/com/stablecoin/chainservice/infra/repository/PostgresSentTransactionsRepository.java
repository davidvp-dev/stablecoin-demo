package com.stablecoin.chainservice.infra.repository;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.stablecoin.chainservice.models.SentTransaction;

/**
 * sent_transactions. Each method is its own statement and commits on its own
 * (no @Transactional): the point of the table is that what is written before
 * sending a transaction is already on disk if chain-service crashes right after.
 */
@Repository
public class PostgresSentTransactionsRepository {

	private static final String COLUMNS = """
			order_id, onchain_id, chain_id, from_address, to_address, amount_units, nonce, tx_hash,
			status, attempts, failure_reason""";

	private final JdbcClient jdbc;

	public PostgresSentTransactionsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<SentTransaction> findByOrderId(UUID orderId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM sent_transactions WHERE order_id = :orderId")
				.param("orderId", orderId)
				.query((rs, rowNum) -> map(rs))
				.optional();
	}

	/**
	 * Inserts the PENDING row for a new order. ON CONFLICT DO NOTHING: if the row
	 * already exists (redelivered MintApproved), it is left as it is.
	 */
	public void insertPendingIfAbsent(UUID orderId, String onchainId, long chainId, String fromAddress,
			String toAddress, BigInteger amountUnits) {
		jdbc.sql("""
				INSERT INTO sent_transactions (order_id, onchain_id, chain_id, from_address, to_address, amount_units)
				VALUES (:orderId, :onchainId, :chainId, :fromAddress, :toAddress, :amountUnits)
				ON CONFLICT (order_id) DO NOTHING
				""")
				.param("orderId", orderId)
				.param("onchainId", onchainId)
				.param("chainId", chainId)
				.param("fromAddress", fromAddress)
				.param("toAddress", toAddress)
				.param("amountUnits", amountUnits)
				.update();
	}

	/**
	 * Saves the nonce and hash of a signed transaction BEFORE it is sent, so a
	 * crash between sending and the next write leaves a hash to look up.
	 */
	public void saveSigned(UUID orderId, long nonce, String txHash) {
		jdbc.sql("""
				UPDATE sent_transactions SET nonce = :nonce, tx_hash = :txHash, updated_at = now()
				WHERE order_id = :orderId
				""")
				.param("orderId", orderId)
				.param("nonce", nonce)
				.param("txHash", txHash)
				.update();
	}

	/**
	 * An attempt failed for a business reason (revert in the simulation, or the
	 * node rejected the transaction), and returns how many have failed so far.
	 * {@code attempts} only counts these: an infrastructure error (node or
	 * database down) is retried without spending attempts.
	 *
	 * The nonce of a rejected transaction was not used, so it is cleared:
	 * otherwise the next order, which gets the same nonce from the node, would
	 * break UNIQUE (chain_id, from_address, nonce).
	 */
	public int recordFailedAttempt(UUID orderId, String reason) {
		return jdbc.sql("""
				UPDATE sent_transactions
				SET attempts = attempts + 1, nonce = NULL, tx_hash = NULL, failure_reason = :reason, updated_at = now()
				WHERE order_id = :orderId AND status = 'PENDING'
				RETURNING attempts
				""")
				.param("orderId", orderId)
				.param("reason", reason)
				.query(Integer.class)
				.single();
	}

	public void markSubmitted(UUID orderId) {
		jdbc.sql("""
				UPDATE sent_transactions SET status = 'SUBMITTED', failure_reason = NULL, updated_at = now()
				WHERE order_id = :orderId AND status = 'PENDING'
				""")
				.param("orderId", orderId)
				.update();
	}

	/** From PENDING (retries exhausted) or SUBMITTED (the transaction reverted on-chain). */
	public void markFailed(UUID orderId, String reason) {
		jdbc.sql("""
				UPDATE sent_transactions SET status = 'FAILED', failure_reason = :reason, updated_at = now()
				WHERE order_id = :orderId AND status IN ('PENDING', 'SUBMITTED')
				""")
				.param("orderId", orderId)
				.param("reason", reason)
				.update();
	}

	/**
	 * The TokensEmitted log of this order reached N confirmations. Also from
	 * PENDING: if MintSubmitted was never recorded (crash), the chain still wins.
	 */
	public void markConfirmed(String onchainId, String txHash) {
		jdbc.sql("""
				UPDATE sent_transactions
				SET status = 'CONFIRMED', tx_hash = COALESCE(tx_hash, :txHash), updated_at = now()
				WHERE onchain_id = :onchainId AND status IN ('PENDING', 'SUBMITTED')
				""")
				.param("onchainId", onchainId)
				.param("txHash", txHash)
				.update();
	}

	/** SUBMITTED transactions whose receipt hasn't been checked for a revert yet. */
	public List<SentTransaction> findSubmitted() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM sent_transactions WHERE status = 'SUBMITTED' ORDER BY updated_at")
				.query((rs, rowNum) -> map(rs))
				.list();
	}

	private static SentTransaction map(ResultSet rs) throws SQLException {
		long nonce = rs.getLong("nonce");
		Long nullableNonce = rs.wasNull() ? null : nonce;
		return new SentTransaction(
				rs.getObject("order_id", UUID.class),
				rs.getString("onchain_id"),
				rs.getLong("chain_id"),
				rs.getString("from_address"),
				rs.getString("to_address"),
				rs.getBigDecimal("amount_units").toBigIntegerExact(),
				nullableNonce,
				rs.getString("tx_hash"),
				rs.getString("status"),
				rs.getInt("attempts"),
				rs.getString("failure_reason"));
	}
}
