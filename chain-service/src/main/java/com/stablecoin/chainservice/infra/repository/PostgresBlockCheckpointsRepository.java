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
}
