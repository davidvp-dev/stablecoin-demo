package com.stablecoin.chainservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Contract event reader ({@code app.reader.*}).
 *
 * @param interval      pause between runs
 * @param startBlock    first block to read when there is no checkpoint yet
 * @param maxBlockRange most blocks per eth_getLogs call
 */
@ConfigurationProperties(prefix = "app.reader")
public record ReaderProperties(Duration interval, long startBlock, int maxBlockRange) {
}
