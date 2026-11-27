package com.stablecoin.chainservice.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stablecoin.chainservice.infra.clients.KafkaPublisher;
import com.stablecoin.chainservice.models.EventEnvelope;
import com.stablecoin.chainservice.util.Constants;
import com.stablecoin.chainservice.util.EventIds;

import tools.jackson.databind.ObjectMapper;

/**
 * Builds the envelopes chain-service produces (docs/events.md) and publishes
 * MintSubmitted / MintFailed straight to chain-events.v1. MintConfirmed and
 * RedeemConfirmed go through observed_events first (ChainEventsReader).
 *
 * Every event id is deterministic, so publishing the same fact twice (a
 * redelivered MintApproved, a crash before the offset commit) produces a
 * duplicate that core-api skips by eventId.
 */
@Service
public class ChainEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(ChainEventPublisher.class);

	private final KafkaPublisher kafkaPublisher;
	private final ObjectMapper objectMapper;

	public ChainEventPublisher(KafkaPublisher kafkaPublisher, ObjectMapper objectMapper) {
		this.kafkaPublisher = kafkaPublisher;
		this.objectMapper = objectMapper;
	}

	/** The node accepted the mint transaction. */
	public void publishMintSubmitted(UUID orderId, String txHash, long nonce) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("txHash", txHash);
		payload.put("nonce", nonce);
		publish(Constants.MINT_SUBMITTED, orderId, payload);
	}

	/**
	 * The mint can't happen: retries exhausted, or the transaction reverted on-chain.
	 *
	 * @param txHash the reverted transaction, or null when none was accepted
	 */
	public void publishMintFailed(UUID orderId, String reason, String txHash) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("reason", reason);
		payload.put("txHash", txHash);
		publish(Constants.MINT_FAILED, orderId, payload);
	}

	/** Serializes an envelope; used for the rows of observed_events. */
	public String toJson(EventEnvelope envelope) {
		return objectMapper.writeValueAsString(envelope);
	}

	private void publish(String eventType, UUID orderId, Map<String, Object> payload) {
		EventEnvelope envelope = new EventEnvelope(EventIds.forOrder(eventType, orderId), eventType,
				Constants.EVENT_VERSION, Instant.now().toString(), orderId, payload);
		kafkaPublisher.sendAndWait(Constants.CHAIN_EVENTS_TOPIC, orderId.toString(), toJson(envelope));
		log.info("Published {} {} for order {}", eventType, envelope.eventId(), orderId);
	}
}
