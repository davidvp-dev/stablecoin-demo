package com.stablecoin.chainservice.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stablecoin.chainservice.service.ChainEventsReader;
import com.stablecoin.chainservice.service.RpcUnavailableException;

/**
 * Runs the contract event reader every app.reader.interval. fixedDelay counts
 * from the end of the previous run, and Spring's single scheduler thread means
 * runs never overlap.
 */
@Component
public class ChainEventsReaderJob {

	private static final Logger log = LoggerFactory.getLogger(ChainEventsReaderJob.class);

	private final ChainEventsReader reader;

	public ChainEventsReaderJob(ChainEventsReader reader) {
		this.reader = reader;
	}

	@Scheduled(fixedDelayString = "${app.reader.interval:2s}", initialDelayString = "${app.reader.interval:2s}")
	public void run() {
		try {
			// Several ranges in a row when far behind (first start on Sepolia).
			while (!reader.readNextRange()) {
				// next range
			}
			reader.publishPending();
			reader.checkSubmittedReceipts();
		} catch (RpcUnavailableException e) {
			log.warn("Event reader: {}; retrying on the next run", e.getMessage());
		} catch (RuntimeException e) {
			log.error("Event reader run failed; retrying on the next run", e);
		}
	}
}
