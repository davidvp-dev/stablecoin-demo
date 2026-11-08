package com.stablecoin.coreapi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * The key pair is generated in each test, so no private key is stored in the repository.
 */
class SignatureUtilsTest {

	private static final String MESSAGE = "localhost:5173 wants you to sign in with your Ethereum account:\nNonce: abc123";

	@Test
	void recoversTheSigner() throws Exception {
		ECKeyPair keyPair = Keys.createEcKeyPair();
		String expected = Keys.toChecksumAddress(Keys.getAddress(keyPair));

		String signer = SignatureUtils.recoverPersonalSignSigner(MESSAGE, sign(MESSAGE, keyPair, false));

		assertEquals(expected, signer);
	}

	@Test
	void acceptsVAsZeroOrOne() throws Exception {
		ECKeyPair keyPair = Keys.createEcKeyPair();
		String expected = Keys.toChecksumAddress(Keys.getAddress(keyPair));

		String signer = SignatureUtils.recoverPersonalSignSigner(MESSAGE, sign(MESSAGE, keyPair, true));

		assertEquals(expected, signer);
	}

	@Test
	void differentMessageRecoversAnotherAddress() throws Exception {
		ECKeyPair keyPair = Keys.createEcKeyPair();
		String expected = Keys.toChecksumAddress(Keys.getAddress(keyPair));

		String signer = SignatureUtils.recoverPersonalSignSigner(MESSAGE + " tampered", sign(MESSAGE, keyPair, false));

		assertNotEquals(expected, signer);
	}

	@Test
	void rejectsWrongLength() {
		assertThrows(IllegalArgumentException.class, () -> SignatureUtils.recoverPersonalSignSigner(MESSAGE, "0x1234"));
	}

	@Test
	void rejectsInvalidV() throws Exception {
		String signature = sign(MESSAGE, Keys.createEcKeyPair(), false);
		String badV = signature.substring(0, signature.length() - 2) + "1f"; // v = 31

		assertThrows(IllegalArgumentException.class, () -> SignatureUtils.recoverPersonalSignSigner(MESSAGE, badV));
	}

	/** Signs like a wallet's personal_sign and returns 0x + r + s + v. */
	private static String sign(String message, ECKeyPair keyPair, boolean vAsZeroOrOne) {
		Sign.SignatureData data = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), keyPair);
		byte[] signature = new byte[65];
		System.arraycopy(data.getR(), 0, signature, 0, 32);
		System.arraycopy(data.getS(), 0, signature, 32, 32);
		signature[64] = vAsZeroOrOne ? (byte) (data.getV()[0] - 27) : data.getV()[0];
		return Numeric.toHexString(signature);
	}
}
