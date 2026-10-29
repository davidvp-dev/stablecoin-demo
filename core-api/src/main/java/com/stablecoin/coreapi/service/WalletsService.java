package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletChallengeRequest;
import com.stablecoin.coreapi.generated.model.WalletChallengeResponse;
import com.stablecoin.coreapi.generated.model.WalletChallengeResponseChallenge;
import com.stablecoin.coreapi.generated.model.WalletVerificationRequest;
import com.stablecoin.coreapi.generated.model.WalletVerificationResponse;
import com.stablecoin.coreapi.util.Constants;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.web3j.crypto.Keys;

@Service
public class WalletsService {

	@Value("${app.name}")
	public String appName;

	@Value("${app.domain}")
	public String domain;

	@Value("${app.URI}")
	public String uri;

	@Value("${app.chainId}")
	public String chainId;

	@Value("${app.challengeExpirationSeconds}")
	public int challengeExpirationSeconds;

	private static final SecureRandom secureRandom = new SecureRandom();

	public WalletChallengeResponse generateWalletChallenge(WalletChallengeRequest request) {
		WalletChallengeResponseChallenge challenge = new WalletChallengeResponseChallenge();
		WalletChallengeResponse response = new WalletChallengeResponse();

		String checksumAddress = validateWalletAddress(request.getWalletAddress());
		String nonce = new BigInteger(130, secureRandom).toString(32);
		Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plusSeconds(challengeExpirationSeconds);

		// TO-DO: Insert the nonce and wallet address into the database for later
		// verification

		challenge.setMessage(generateMessage(request, checksumAddress, nonce, issuedAt, expiresAt));
		challenge.setNonce(nonce);
		challenge.setIssuedAt(issuedAt.atOffset(ZoneOffset.UTC));
		challenge.setExpiresAt(expiresAt.atOffset(ZoneOffset.UTC));
		response.setChallenge(challenge);
		return response;
	}

	private String validateWalletAddress(String address) {
		if (address == null || address.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wallet address is required");
		}
		try {
			Keys.toChecksumAddress(address); // Validate Ethereum address format
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Ethereum wallet address");
		}
		return address;
	}

	private String generateMessage(WalletChallengeRequest request, String address, String nonce, Instant issuedAt,
			Instant expiresAt) {

		String message = Constants.SIWE_MESSAGE_TEMPLATE
				.replace("{domain}", domain)
				.replace("{address}", address)
				.replace("{appName}", appName)
				.replace("{uri}", uri)
				.replace("{chainId}", String.valueOf(chainId))
				.replace("{nonce}", nonce)
				.replace("{issuedAt}", issuedAt.toString())
				.replace("{expiresAt}", expiresAt.toString());

		if (message.contains("{")) {
			throw new IllegalStateException("Unresolved placeholder in SIWE message");
		}

		return message;
	}

	public Wallet getWalletInfo(String address) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Wallet lookup is not implemented");
	}

	public WalletVerificationResponse verifyWalletSignature(WalletVerificationRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
				"Wallet signature verification is not implemented");
	}
}
