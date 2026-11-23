package com.stablecoin.coreapi.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.stablecoin.coreapi.util.Constants;

/**
 * The broker has auto-creation off (KAFKA_AUTO_CREATE_TOPICS_ENABLE=false in
 * docker-compose.yml), so the topics from docs/events.md are declared here.
 * Spring Boot's KafkaAdmin creates any that are missing when the app starts,
 * and leaves existing ones as they are.
 */
@Configuration
public class KafkaTopicsConfig {

	@Value("${app.kafka.topicPartitions:3}")
	private int partitions;

	@Value("${app.kafka.topicReplicas:1}")
	private int replicas;

	@Bean
	public NewTopic mintOrdersTopic() {
		return TopicBuilder.name(Constants.MINT_ORDERS_TOPIC).partitions(partitions).replicas(replicas).build();
	}

	@Bean
	public NewTopic chainEventsTopic() {
		return TopicBuilder.name(Constants.CHAIN_EVENTS_TOPIC).partitions(partitions).replicas(replicas).build();
	}
}
