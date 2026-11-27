package com.stablecoin.chainservice.config;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

import com.stablecoin.chainservice.util.Constants;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ MintProperties.class, ReaderProperties.class })
public class KafkaConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

	@Value("${app.kafka.topicPartitions:3}")
	private int partitions;

	@Value("${app.kafka.topicReplicas:1}")
	private int replicas;

	/**
	 * The same topics core-api declares. KafkaAdmin creates the ones that don't
	 * exist yet and leaves the others alone, so whichever service starts first
	 * creates them.
	 */
	@Bean
	public NewTopic mintOrdersTopic() {
		return TopicBuilder.name(Constants.MINT_ORDERS_TOPIC).partitions(partitions).replicas(replicas).build();
	}

	@Bean
	public NewTopic chainEventsTopic() {
		return TopicBuilder.name(Constants.CHAIN_EVENTS_TOPIC).partitions(partitions).replicas(replicas).build();
	}

	/**
	 * What happens when MintApprovedListener throws. Business failures (reverts,
	 * node rejections) never get here: MintSender handles them and publishes
	 * MintFailed. What gets here is infrastructure: the node, the database or
	 * Kafka is down. Skipping the message would lose a paid mint for good,
	 * because core-api never sends MintApproved again. So it is retried every
	 * backoff, without limit, and the partition waits; the offset is only
	 * committed once it succeeds.
	 *
	 * Spring Boot plugs a CommonErrorHandler bean into the listener container.
	 */
	@Bean
	public CommonErrorHandler kafkaErrorHandler(@Value("${app.kafka.listenerRetryBackoff:5s}") Duration backoff) {
		DefaultErrorHandler handler = new DefaultErrorHandler(
				new FixedBackOff(backoff.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
		handler.setRetryListeners((record, ex, attempt) -> log.warn(
				"MintApproved at offset {} failed (attempt {}), retrying in {}: {}",
				record.offset(), attempt, backoff, ex.getMessage()));
		return handler;
	}
}
