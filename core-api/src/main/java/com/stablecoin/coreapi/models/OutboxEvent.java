package com.stablecoin.coreapi.models;

import java.util.UUID;

/**
 * One pending row of outbox_events.
 *
 * @param payload the full event envelope as JSON text, sent to Kafka as is
 */
public record OutboxEvent(UUID eventId, String topic, String messageKey, String eventType, String payload) {
}
