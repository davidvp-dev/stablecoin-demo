package com.stablecoin.chainservice.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Selectors checked against `cast sig "<signature>"`. */
class RevertDecoderTest {

	@Test
	void decodesKnownCustomErrors() {
		assertEquals("EnforcedPause()", RevertDecoder.describe("0xd93c0665"));
		assertEquals("UserBlacklisted(address)",
				RevertDecoder.describe("0x7f8b7dc8000000000000000000000000a0ee7a142d267c1f36714e4a8f75612f20a79720"));
		assertEquals(RevertDecoder.MINT_ORDER_ALREADY_SUBMITTED, RevertDecoder.describe("0xed1d787f" + "00".repeat(32)));
	}

	@Test
	void reportsUnknownOrMissingData() {
		assertEquals("unknown error 0x12345678", RevertDecoder.describe("0x12345678"));
		assertEquals("execution reverted without data", RevertDecoder.describe("0x"));
		assertEquals("execution reverted without data", RevertDecoder.describe(null));
	}
}
