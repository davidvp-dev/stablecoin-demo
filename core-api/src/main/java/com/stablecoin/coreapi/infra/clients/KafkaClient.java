package com.stablecoin.coreapi.infra.clients;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends a message and waits until the broker acknowledges it. The outbox
 * publisher only marks a row as sent after this returns, so a failure leaves
 * the row pending and it is sent again on the next run.
 */
@Component
public class KafkaClient {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final Duration sendTimeout;

	public KafkaClient(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${app.kafka.sendTimeout:15s}") Duration sendTimeout) {
		this.kafkaTemplate = kafkaTemplate;
		this.sendTimeout = sendTimeout;
	}

	/**
	 * @throws KafkaSendException if the broker doesn't acknowledge the message
	 *                            within the send timeout
	 */
	public void sendAndWait(String topic, String key, String value) {
		try {
			kafkaTemplate.send(topic, key, value).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new KafkaSendException("Interrupted while sending to " + topic, e);
		} catch (ExecutionException e) {
			throw new KafkaSendException("Kafka rejected the message for " + topic, e.getCause());
		} catch (TimeoutException e) {
			throw new KafkaSendException("No ack from Kafka for " + topic + " within " + sendTimeout, e);
		}
	}

	public static class KafkaSendException extends RuntimeException {
		public KafkaSendException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
