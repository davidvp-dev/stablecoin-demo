package com.stablecoin.coreapi.infra.repository;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
}
