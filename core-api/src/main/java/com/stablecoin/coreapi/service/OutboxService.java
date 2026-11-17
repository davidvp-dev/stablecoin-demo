package com.stablecoin.coreapi.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;

import com.stablecoin.coreapi.infra.repository.PostgresOutboxRepository;
import com.stablecoin.coreapi.models.EventEnvelope;
import com.stablecoin.coreapi.models.MintOrderData;
import com.stablecoin.coreapi.util.Constants;

import tools.jackson.databind.ObjectMapper;

/**
 * Builds the events core-api produces and writes them to the outbox. It never
 * talks to Kafka: the outbox publisher job does that (Day 2, Outbox).
 */
@Service
public class OutboxService {

	public static final String MINT_APPROVED = "MintApproved";

	private final PostgresOutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;

	public OutboxService(PostgresOutboxRepository outboxRepository, ObjectMapper objectMapper) {
		this.outboxRepository = outboxRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * Queues MintApproved for chain-service. Must run in the same transaction as
	 * the order's change to APPROVED.
	 */
	public void enqueueMintApproved(MintOrderData order) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("wallet", Keys.toChecksumAddress(order.getWalletAddress()));
		payload.put("amountCents", order.getAmountCents());

		// Random v4 id, generated once here: a retry of the publisher sends the
		// same row, so consumers see the same eventId.
		EventEnvelope event = new EventEnvelope(UUID.randomUUID(), MINT_APPROVED, EventEnvelope.VERSION,
				Instant.now().toString(), order.getId(), payload);

		outboxRepository.insertOutboxEvent(event.eventId(), Constants.MINT_ORDERS_TOPIC,
				order.getId().toString(), MINT_APPROVED, objectMapper.writeValueAsString(event));
	}
}
