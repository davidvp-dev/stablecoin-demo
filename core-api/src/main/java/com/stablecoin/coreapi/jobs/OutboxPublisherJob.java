package com.stablecoin.coreapi.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stablecoin.coreapi.service.OutboxService;

/**
 * Sends the pending outbox rows to Kafka. fixedDelay counts from the end of
 * the previous run, so a slow run (Kafka down, waiting for timeouts) never
 * stacks up with the next one.
 */
@Component
public class OutboxPublisherJob {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

	private final OutboxService outboxService;
	private final int batchSize;

	public OutboxPublisherJob(OutboxService outboxService, @Value("${app.outbox.batchSize:100}") int batchSize) {
		this.outboxService = outboxService;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${app.outbox.publishInterval:1s}", initialDelayString = "${app.outbox.publishInterval:1s}")
	public void publishPending() {
		try {
			// A full batch means more rows are probably waiting: keep going instead
			// of waiting for the next tick.
			while (outboxService.publishPendingBatch(batchSize) == batchSize) {
				// next batch
			}
		} catch (RuntimeException e) {
			// e.g. the database is down. The job keeps its schedule and retries.
			log.error("Outbox publisher run failed", e);
		}
	}
}
