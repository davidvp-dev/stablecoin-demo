package com.stablecoin.chainservice.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.web3j.utils.Numeric;

/**
 * The two conversion rules of docs/events.md (RF-CHAIN-02). They live only in
 * chain-service and the portal: core-api always works in cents and UUIDs.
 */
public final class Conversions {

	/** EURD has 6 decimals and the euro 2, so 1 cent = 10^4 units. */
	public static final BigInteger UNITS_PER_CENT = BigInteger.valueOf(10_000);

	private Conversions() {
	}

	/** 1234 cents (12.34 €) → 12,340,000 units. */
	public static BigInteger centsToUnits(long amountCents) {
		return BigInteger.valueOf(amountCents).multiply(UNITS_PER_CENT);
	}

	/**
	 * 12,340,000 units → 1234 cents. Empty when the units are not a whole number
	 * of cents (possible in redeem(), which accepts any amount) or don't fit in a
	 * long.
	 */
	public static Optional<Long> unitsToCents(BigInteger units) {
		BigInteger[] quotientAndRemainder = units.divideAndRemainder(UNITS_PER_CENT);
		if (quotientAndRemainder[1].signum() != 0 || quotientAndRemainder[0].bitLength() > 63) {
			return Optional.empty();
		}
		return Optional.of(quotientAndRemainder[0].longValueExact());
	}

	/**
	 * The UUID's 16 bytes right-aligned in the 32-byte word:
	 * 9b2e4c1a-7d3f-4e8b-9a2c-5d6e7f8a9b0c → 0x00..00 9b2e4c1a7d3f4e8b9a2c5d6e7f8a9b0c
	 */
	public static byte[] orderIdToBytes32(UUID orderId) {
		ByteBuffer buffer = ByteBuffer.allocate(32);
		buffer.position(16);
		buffer.putLong(orderId.getMostSignificantBits());
		buffer.putLong(orderId.getLeastSignificantBits());
		return buffer.array();
	}

	/**
	 * The inverse rule. Empty when the first 16 bytes are not zero: that bytes32
	 * can't belong to a core-api order (for example, a redeem() with a made-up id).
	 */
	public static Optional<UUID> bytes32ToOrderId(byte[] bytes32) {
		if (bytes32.length != 32) {
			throw new IllegalArgumentException("Expected 32 bytes, got " + bytes32.length);
		}
		for (int i = 0; i < 16; i++) {
			if (bytes32[i] != 0) {
				return Optional.empty();
			}
		}
		ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(bytes32, 16, 32));
		return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
	}

	/** bytes32 as lower-case 0x hex (sent_transactions.onchain_id, payload.onchainId). */
	public static String toHex(byte[] bytes32) {
		return Numeric.toHexString(bytes32);
	}
}
