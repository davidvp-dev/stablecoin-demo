package com.stablecoin.chainservice.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/**
 * Deterministic event ids (docs/events.md, "How eventId is generated"). The same
 * fact always gets the same eventId, so a republish after a crash is a
 * duplicate that consumers skip, not a new event.
 */
public final class EventIds {

	/** Project namespace for the UUID v5 ids, fixed in docs/events.md. Never change it. */
	public static final UUID NAMESPACE = UUID.fromString("e60e415b-bae5-42e7-8980-db070c4b56fa");

	private EventIds() {
	}

	/** MintSubmitted / MintFailed: v5 of "<eventType>:<orderId>". */
	public static UUID forOrder(String eventType, UUID orderId) {
		return uuidV5(eventType + ":" + orderId);
	}

	/** MintConfirmed / RedeemConfirmed: v5 of "<chainId>:<txHash>:<logIndex>". */
	public static UUID forLog(long chainId, String txHash, long logIndex) {
		return uuidV5(chainId + ":" + txHash.toLowerCase(Locale.ROOT) + ":" + logIndex);
	}

	/**
	 * RFC 4122 version 5: SHA-1 of namespace bytes + UTF-8 name, first 16 bytes,
	 * with the version (5) and variant (IETF) bits set. Java only ships v3 (MD5),
	 * in UUID.nameUUIDFromBytes.
	 */
	static UUID uuidV5(String name) {
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.update(ByteBuffer.allocate(16)
					.putLong(NAMESPACE.getMostSignificantBits())
					.putLong(NAMESPACE.getLeastSignificantBits())
					.array());
			byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
			hash[6] = (byte) ((hash[6] & 0x0f) | 0x50); // version 5
			hash[8] = (byte) ((hash[8] & 0x3f) | 0x80); // IETF variant
			ByteBuffer buffer = ByteBuffer.wrap(hash, 0, 16);
			return new UUID(buffer.getLong(), buffer.getLong());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 not available", e);
		}
	}
}
