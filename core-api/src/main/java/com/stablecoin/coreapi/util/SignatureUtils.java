package com.stablecoin.coreapi.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Ethereum signature helpers built on web3j.
 */
public final class SignatureUtils {

	/** r (32 bytes) + s (32 bytes) + v (1 byte). */
	private static final int SIGNATURE_LENGTH = 65;

	private SignatureUtils() {
	}

	/**
	 * Recovers the address that signed {@code message} with {@code personal_sign} (EIP-191 version 0x45), which is what
	 * wallets like MetaMask and wagmi's {@code signMessage} use.
	 *
	 * <p>Steps:
	 * <ol>
	 * <li>Decode the hex signature into its 65 bytes and split it into r, s and v.</li>
	 * <li>Normalize v: some wallets send 0/1 instead of 27/28.</li>
	 * <li>web3j prepends the EIP-191 prefix {@code "\x19Ethereum Signed Message:\n" + length} to the message, hashes it
	 * with Keccak-256, and recovers the signer's public key from the hash and (r, s, v).</li>
	 * <li>Derive the address from the public key (last 20 bytes of its Keccak-256 hash).</li>
	 * </ol>
	 *
	 * <p>Recovery always returns <i>some</i> address for a well-formed signature; it doesn't prove anything by itself.
	 * The caller must still compare the result with the expected wallet address.
	 *
	 * @param message the exact message that was signed (the stored SIWE challenge), as UTF-8 text
	 * @param signatureHex the signature as {@code 0x} + 130 hex characters
	 * @return the signer address in EIP-55 checksum format, e.g. {@code 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266}
	 * @throws IllegalArgumentException if the signature is malformed or no public key can be recovered from it
	 */
	public static String recoverPersonalSignSigner(String message, String signatureHex) {
		// 1. Decode the signature and check its length
		byte[] signature = Numeric.hexStringToByteArray(signatureHex);
		if (signature.length != SIGNATURE_LENGTH) {
			throw new IllegalArgumentException(
					"Signature must be " + SIGNATURE_LENGTH + " bytes, got " + signature.length);
		}

		// 2. Split into r, s, v and normalize v to 27/28
		byte[] r = Arrays.copyOfRange(signature, 0, 32);
		byte[] s = Arrays.copyOfRange(signature, 32, 64);
		byte v = signature[64];
		if (v < 27) {
			v += 27;
		}
		if (v != 27 && v != 28) {
			throw new IllegalArgumentException("Invalid signature recovery id v=" + v);
		}
		Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);

		// 3. Add the EIP-191 prefix, hash, and recover the public key
		BigInteger publicKey;
		try {
			publicKey = Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), signatureData);
		} catch (SignatureException e) {
			throw new IllegalArgumentException("Could not recover a public key from the signature", e);
		}

		// 4. Public key -> address, in checksum format
		return Keys.toChecksumAddress(Keys.getAddress(publicKey));
	}
}
