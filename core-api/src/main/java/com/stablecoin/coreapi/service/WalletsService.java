package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletChallenge;
import com.stablecoin.coreapi.generated.model.WalletChallengeRequest;
import com.stablecoin.coreapi.generated.model.WalletVerificationRequest;
import com.stablecoin.coreapi.infra.repository.PostgresWalletChallengeRepository;
import com.stablecoin.coreapi.util.Constants;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
	private final PostgresWalletChallengeRepository walletChallengeRepository;

	public WalletsService(PostgresWalletChallengeRepository walletChallengeRepository) {
		this.walletChallengeRepository = walletChallengeRepository;
	}

	@Transactional
	public WalletChallenge generateWalletChallenge(WalletChallengeRequest request) {
		// Calculate the checksum address and generate a nonce
		String checksumAddress = validateAndChecksumWalletAddress(request.getWalletAddress());
		String storedAddress = checksumAddress.toLowerCase(Locale.ROOT);
		String nonce = new BigInteger(130, secureRandom).toString(32);
		Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plusSeconds(challengeExpirationSeconds);
		String message = generateMessage(checksumAddress, nonce, issuedAt, expiresAt);

		// Store the challenge for later verification (the wallet starts as PENDING)
		walletChallengeRepository.insertPendingWalletIfAbsent(storedAddress);
		walletChallengeRepository.insertIntoWalletChallenges(nonce, storedAddress, message,
				expiresAt.atOffset(ZoneOffset.UTC));

		// Map the response fields
		WalletChallenge challenge = new WalletChallenge();
		challenge.setMessage(message);
		challenge.setNonce(nonce);
		challenge.setIssuedAt(issuedAt.atOffset(ZoneOffset.UTC));
		challenge.setExpiresAt(expiresAt.atOffset(ZoneOffset.UTC));
		return challenge;
	}

	private String validateAndChecksumWalletAddress(String address) {
		if (address == null || address.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wallet address is required");
		}
		try {
			return Keys.toChecksumAddress(address); // Validate Ethereum address format
		} catch (RuntimeException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Ethereum wallet address");
		}
	}

	private String generateMessage(String address, String nonce, Instant issuedAt, Instant expiresAt) {
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

	public Wallet getWallet(String address) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Wallet lookup is not implemented");
	}

	public Wallet verifyWalletSignature(WalletVerificationRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
				"Wallet signature verification is not implemented");
	}
}
