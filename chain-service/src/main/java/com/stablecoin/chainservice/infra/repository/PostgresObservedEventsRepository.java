package com.stablecoin.chainservice.infra.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * observed_events: contract logs that reached N confirmations, with the event
 * envelope to publish. It works as chain-service's outbox for MintConfirmed and
 * RedeemConfirmed: the row and the checkpoint are saved together, and a row
 * stays unpublished until Kafka acknowledges it.
 */
@Repository
public class PostgresObservedEventsRepository {

	/** One unpublished row. key is the Kafka key: the order id, or the bytes32 when there is none. */
	public record PendingEvent(UUID eventId, String eventName, String key, String payload) {
	}

	private final JdbcClient jdbc;

	public PostgresObservedEventsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * ON CONFLICT DO NOTHING on (chain_id, tx_hash, log_index): reading the same
	 * block range again after a crash doesn't create a second row.
	 */
	public void insertIfAbsent(long chainId, String txHash, long logIndex, long blockNumber, String blockHash,
			String eventName, UUID eventId, UUID orderId, String payloadJson) {
		jdbc.sql("""
				INSERT INTO observed_events (chain_id, tx_hash, log_index, block_number, block_hash, event_name,
				                             event_id, order_id, payload)
				VALUES (:chainId, :txHash, :logIndex, :blockNumber, :blockHash, :eventName,
				        :eventId, :orderId, CAST(:payload AS JSONB))
				ON CONFLICT (chain_id, tx_hash, log_index) DO NOTHING
				""")
				.param("chainId", chainId)
				.param("txHash", txHash)
				.param("logIndex", logIndex)
				.param("blockNumber", blockNumber)
				.param("blockHash", blockHash)
				.param("eventName", eventName)
				.param("eventId", eventId)
				.param("orderId", orderId)
				.param("payload", payloadJson)
				.update();
	}

	/** Unpublished rows in chain order (block, then position in the block). */
	public List<PendingEvent> findUnpublished(int limit) {
		return jdbc.sql("""
				SELECT event_id, event_name,
				       COALESCE(order_id::text, payload -> 'payload' ->> 'onchainId') AS message_key,
				       payload::text AS payload
				FROM observed_events
				WHERE published_at IS NULL
				ORDER BY block_number, log_index
				LIMIT :limit
				""")
				.param("limit", limit)
				.query((rs, rowNum) -> new PendingEvent(
						rs.getObject("event_id", UUID.class),
						rs.getString("event_name"),
						rs.getString("message_key"),
						rs.getString("payload")))
				.list();
	}

	public void markPublished(UUID eventId) {
		jdbc.sql("UPDATE observed_events SET published_at = now() WHERE event_id = :eventId")
				.param("eventId", eventId)
				.update();
	}
}
