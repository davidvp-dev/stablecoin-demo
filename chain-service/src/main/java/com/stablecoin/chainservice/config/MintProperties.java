package com.stablecoin.chainservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mint sender ({@code app.mint.*}).
 *
 * @param maxAttempts attempts when the node rejects the transaction or mint()
 *                    would revert; after the last one, MintFailed is published.
 *                    Infrastructure errors (node or database down) don't count:
 *                    the message is retried until they're fixed
 * @param retryWait   fixed wait between attempts
 */
@ConfigurationProperties(prefix = "app.mint")
public record MintProperties(int maxAttempts, Duration retryWait) {
}
