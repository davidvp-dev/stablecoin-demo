package com.stablecoin.chainservice.infra.clients;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends a message and waits for the broker's ack. Callers only record a message
 * as published after this returns.
 */
@Component
public class KafkaPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final Duration sendTimeout;

	public KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${app.kafka.sendTimeout:15s}") Duration sendTimeout) {
		this.kafkaTemplate = kafkaTemplate;
		this.sendTimeout = sendTimeout;
	}

	/** @throws KafkaPublishException if the broker doesn't acknowledge the message in time */
	public void sendAndWait(String topic, String key, String value) {
		try {
			kafkaTemplate.send(topic, key, value).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new KafkaPublishException("Interrupted while sending to " + topic, e);
		} catch (ExecutionException e) {
			throw new KafkaPublishException("Kafka rejected the message for " + topic, e.getCause());
		} catch (TimeoutException e) {
			throw new KafkaPublishException("No ack from Kafka for " + topic + " within " + sendTimeout, e);
		}
	}

	public static class KafkaPublishException extends RuntimeException {
		public KafkaPublishException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
