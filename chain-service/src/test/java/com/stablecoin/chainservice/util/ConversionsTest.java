package com.stablecoin.chainservice.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

/** The examples of docs/events.md, "Conversion rules". */
class ConversionsTest {

	private static final UUID ORDER_ID = UUID.fromString("9b2e4c1a-7d3f-4e8b-9a2c-5d6e7f8a9b0c");
	private static final String BYTES32 = "0x000000000000000000000000000000009b2e4c1a7d3f4e8b9a2c5d6e7f8a9b0c";

	@Test
	void centsToUnits_usesTenThousandUnitsPerCent() {
		assertEquals(BigInteger.valueOf(12_340_000), Conversions.centsToUnits(1234));
	}

	@Test
	void unitsToCents_roundTrips() {
		assertEquals(Optional.of(1234L), Conversions.unitsToCents(BigInteger.valueOf(12_340_000)));
	}

	@Test
	void unitsToCents_isEmptyWhenNotAWholeCent() {
		// redeem() accepts any amount, e.g. 1 unit
		assertTrue(Conversions.unitsToCents(BigInteger.ONE).isEmpty());
		assertTrue(Conversions.unitsToCents(BigInteger.valueOf(12_340_001)).isEmpty());
	}

	@Test
	void unitsToCents_isEmptyWhenTooLargeForALong() {
		assertTrue(Conversions.unitsToCents(BigInteger.TWO.pow(80).multiply(Conversions.UNITS_PER_CENT)).isEmpty());
	}

	@Test
	void orderIdToBytes32_rightAlignsTheUuid() {
		assertEquals(BYTES32, Conversions.toHex(Conversions.orderIdToBytes32(ORDER_ID)));
	}

	@Test
	void bytes32ToOrderId_isTheInverse() {
		assertEquals(Optional.of(ORDER_ID), Conversions.bytes32ToOrderId(Numeric.hexStringToByteArray(BYTES32)));
	}

	@Test
	void bytes32ToOrderId_isEmptyForAForeignId() {
		byte[] foreign = Numeric.hexStringToByteArray(BYTES32);
		foreign[0] = 1; // first 16 bytes not zero: not a core-api order
		assertTrue(Conversions.bytes32ToOrderId(foreign).isEmpty());
	}

	@Test
	void orderIdToBytes32_hasThirtyTwoBytes() {
		assertArrayEquals(Numeric.hexStringToByteArray(BYTES32), Conversions.orderIdToBytes32(ORDER_ID));
	}
}
