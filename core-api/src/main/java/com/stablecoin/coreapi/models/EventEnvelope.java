package com.stablecoin.coreapi.models;

import java.util.Map;
import java.util.UUID;

/**
 * Envelope shared by every Kafka event (docs/events.md). It is serialized to
 * JSON once, when the event is written to the outbox, and published as is.
 *
 * @param occurredAt ISO-8601 UTC instant of the fact, not of the publication
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int version,
        String occurredAt,
        UUID orderId,
        Map<String, Object> payload) {

    /** Version of the event contract in docs/events.md. */
    public static final int VERSION = 1;
}
