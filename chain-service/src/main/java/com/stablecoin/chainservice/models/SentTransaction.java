package com.stablecoin.chainservice.models;

import java.math.BigInteger;
import java.util.UUID;

/**
 * One row of sent_transactions: the mint chain-service sends (or sent) for an
 * order. The primary key is the order id, so an order can never get a second row.
 *
 * @param onchainId   bytes32 of the order id, lower-case 0x hex
 * @param nonce       null until the first attempt is signed
 * @param txHash      hash of the last signed attempt, saved before it is sent
 * @param status      PENDING, SUBMITTED, CONFIRMED or FAILED
 */
public record SentTransaction(
		UUID orderId,
		String onchainId,
		long chainId,
		String fromAddress,
		String toAddress,
		BigInteger amountUnits,
		Long nonce,
		String txHash,
		String status,
		int attempts,
		String failureReason) {

	public static final String PENDING = "PENDING";
	public static final String SUBMITTED = "SUBMITTED";
	public static final String CONFIRMED = "CONFIRMED";
	public static final String FAILED = "FAILED";
}
