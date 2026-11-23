package com.stablecoin.coreapi.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;

import com.stablecoin.coreapi.infra.clients.KafkaClient;
import com.stablecoin.coreapi.infra.clients.KafkaClient.KafkaSendException;
import com.stablecoin.coreapi.infra.repository.PostgresOutboxRepository;
import com.stablecoin.coreapi.models.EventEnvelope;
import com.stablecoin.coreapi.models.MintOrderData;
import com.stablecoin.coreapi.models.OutboxEvent;
import com.stablecoin.coreapi.util.Constants;

import tools.jackson.databind.ObjectMapper;

/**
 * Transactional outbox (RF-CORE-10). Writing: the events core-api produces are
 * built here and saved in the caller's transaction. Publishing:
 * {@link #publishPendingBatch(int)}, run by OutboxPublisherJob, sends them to
 * Kafka.
 */
@Service
public class OutboxService {

	public static final String MINT_APPROVED = "MintApproved";

	private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

	private final PostgresOutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;
	private final KafkaClient kafkaClient;

	public OutboxService(PostgresOutboxRepository outboxRepository, ObjectMapper objectMapper,
			KafkaClient kafkaClient) {
		this.outboxRepository = outboxRepository;
		this.objectMapper = objectMapper;
		this.kafkaClient = kafkaClient;
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

	/**
	 * Publishes up to {@code batchSize} pending events, oldest first, and marks
	 * each one as published once Kafka acknowledges it.
	 *
	 * Delivery is at least once: if the app stops after the ack but before the
	 * commit, the row is still pending and is sent again with the same eventId,
	 * which consumers skip (docs/events.md).
	 *
	 * The first failure stops the batch. Sending later rows would put the events
	 * of an order out of order (all of them share its key, so its partition).
	 * The rows already sent are committed; the rest wait for the next run.
	 *
	 * @return how many events were published
	 */
	@Transactional
	public int publishPendingBatch(int batchSize) {
		List<OutboxEvent> pending = outboxRepository.lockPendingOutboxEvents(batchSize);
		int published = 0;
		for (OutboxEvent event : pending) {
			try {
				kafkaClient.sendAndWait(event.topic(), event.messageKey(), event.payload());
			} catch (KafkaSendException e) {
				log.warn("Outbox: could not publish {} {} (key {}), will retry on the next run: {}",
						event.eventType(), event.eventId(), event.messageKey(), e.getMessage());
				break;
			}
			outboxRepository.markOutboxEventPublished(event.eventId());
			published++;
		}
		if (published > 0) {
			log.info("Outbox: published {} of {} pending events", published, pending.size());
		}
		return published;
	}
}
