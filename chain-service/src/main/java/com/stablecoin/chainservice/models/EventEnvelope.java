package com.stablecoin.chainservice.models;

import java.util.Map;
import java.util.UUID;

/**
 * Envelope shared by every Kafka event (docs/events.md). Same shape as core-api's.
 *
 * @param orderId    null only for a log whose bytes32 is not a core-api order id
 * @param occurredAt ISO-8601 UTC instant of the fact, not of the publication
 */
public record EventEnvelope(
		UUID eventId,
		String eventType,
		int version,
		String occurredAt,
		UUID orderId,
		Map<String, Object> payload) {
}
