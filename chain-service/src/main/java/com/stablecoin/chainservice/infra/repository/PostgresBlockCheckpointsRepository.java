package com.stablecoin.chainservice.infra.repository;

import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresBlockCheckpointsRepository {

	private static final String SELECT_LAST_PROCESSED_BLOCK_QUERY = """
			SELECT last_processed_block
			FROM block_checkpoints
			WHERE chain_id = :chainId AND contract_address = :contractAddress
			""";

	private static final String UPSERT_LAST_PROCESSED_BLOCK_QUERY = """
			INSERT INTO block_checkpoints (chain_id, contract_address, last_processed_block)
			VALUES (:chainId, :contractAddress, :block)
			ON CONFLICT (chain_id, contract_address)
			DO UPDATE SET last_processed_block = EXCLUDED.last_processed_block, updated_at = now()
			""";

	private final JdbcClient jdbc;

	public PostgresBlockCheckpointsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Last block the event reader fully processed for this contract, or empty
	 * until it saves its first checkpoint. Addresses are stored in lower case.
	 */
	public Optional<Long> findLastProcessedBlock(long chainId, String contractAddress) {
		return jdbc.sql(SELECT_LAST_PROCESSED_BLOCK_QUERY)
				.param("chainId", chainId)
				.param("contractAddress", contractAddress.toLowerCase(Locale.ROOT))
				.query(Long.class)
				.optional();
	}

	public void saveLastProcessedBlock(long chainId, String contractAddress, long block) {
		jdbc.sql(UPSERT_LAST_PROCESSED_BLOCK_QUERY)
				.param("chainId", chainId)
				.param("contractAddress", contractAddress.toLowerCase(Locale.ROOT))
				.param("block", block)
				.update();
	}
}
