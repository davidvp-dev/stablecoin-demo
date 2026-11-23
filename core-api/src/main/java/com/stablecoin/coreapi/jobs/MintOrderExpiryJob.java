package com.stablecoin.coreapi.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stablecoin.coreapi.service.MintService;

/**
 * Moves unpaid mint orders to EXPIRED once their expires_at (creation + 24 h)
 * has passed. The bank simulator already refuses to pay an order past its
 * expiry, so a run every minute is only about the status shown to the user.
 */
@Component
public class MintOrderExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(MintOrderExpiryJob.class);

	private final MintService mintService;
	private final int batchSize;

	public MintOrderExpiryJob(MintService mintService, @Value("${app.expiry.batchSize:500}") int batchSize) {
		this.mintService = mintService;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${app.expiry.interval:1m}", initialDelayString = "${app.expiry.initialDelay:10s}")
	public void expireOverdueOrders() {
		try {
			while (mintService.expireOverdueMintOrders(batchSize) == batchSize) {
				// next batch
			}
		} catch (RuntimeException e) {
			log.error("Mint order expiry run failed", e);
		}
	}
}
