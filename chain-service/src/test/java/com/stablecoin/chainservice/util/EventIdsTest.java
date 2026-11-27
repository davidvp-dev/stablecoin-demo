package com.stablecoin.chainservice.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Expected values computed with an independent implementation (Python's
 * uuid.uuid5 and the namespace of docs/events.md), so a consumer in any language
 * gets the same ids.
 */
class EventIdsTest {

	private static final UUID ORDER_ID = UUID.fromString("9b2e4c1a-7d3f-4e8b-9a2c-5d6e7f8a9b0c");

	@Test
	void forOrder_matchesRfc4122Version5() {
		UUID id = EventIds.forOrder("MintSubmitted", ORDER_ID);
		assertEquals(UUID.fromString("aa601440-2636-57ca-a079-80c4a06cdd5f"), id);
		assertEquals(5, id.version());
		assertEquals(2, id.variant()); // IETF
	}

	@Test
	void forLog_matchesRfc4122Version5AndIgnoresHashCase() {
		String txHash = "0x5d1c6f2a9e0b7d3c4a8f1e2b6c9d0a7e3f5b8c1d2e4f6a9b0c3d5e7f8a1b2c3d";
		assertEquals(UUID.fromString("c60b6196-da6d-5471-8b85-e9c3e226f0ed"), EventIds.forLog(31337, txHash, 2));
		assertEquals(EventIds.forLog(31337, txHash, 2), EventIds.forLog(31337, txHash.toUpperCase().replace("0X", "0x"), 2));
	}

	@Test
	void differentEventsOfTheSameOrderGetDifferentIds() {
		assertNotEquals(EventIds.forOrder("MintSubmitted", ORDER_ID), EventIds.forOrder("MintFailed", ORDER_ID));
	}
}
