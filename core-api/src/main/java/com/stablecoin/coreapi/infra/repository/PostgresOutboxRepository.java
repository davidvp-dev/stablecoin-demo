package com.stablecoin.coreapi.infra.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.stablecoin.coreapi.models.OutboxEvent;
import com.stablecoin.coreapi.util.Constants;

@Repository
public class PostgresOutboxRepository {

    private final JdbcClient jdbc;

    public PostgresOutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Adds an event to the outbox. Call it in the same transaction as the state
     * change the event announces, so both are saved or neither is.
     */
    public void insertOutboxEvent(UUID eventId, String topic, String messageKey, String eventType,
            String payloadJson) {
        jdbc.sql(Constants.INSERT_OUTBOX_EVENT_QUERY)
                .param("eventId", eventId)
                .param("topic", topic)
                .param("messageKey", messageKey)
                .param("eventType", eventType)
                .param("payload", payloadJson)
                .update();
    }

    /**
     * Locks and returns up to {@code limit} unpublished events, oldest first.
     * Must run inside a transaction; the locks are held until it ends.
     */
    public List<OutboxEvent> lockPendingOutboxEvents(int limit) {
        return jdbc.sql(Constants.SELECT_PENDING_OUTBOX_EVENTS_QUERY)
                .param("limit", limit)
                .query((rs, rowNum) -> new OutboxEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("topic"),
                        rs.getString("message_key"),
                        rs.getString("event_type"),
                        rs.getString("payload")))
                .list();
    }

    public void markOutboxEventPublished(UUID eventId) {
        jdbc.sql(Constants.MARK_OUTBOX_EVENT_PUBLISHED_QUERY)
                .param("eventId", eventId)
                .update();
    }
}
