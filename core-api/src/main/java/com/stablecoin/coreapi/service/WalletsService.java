package com.stablecoin.coreapi.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletChallenge;
import com.stablecoin.coreapi.generated.model.WalletChallengeRequest;
import com.stablecoin.coreapi.generated.model.WalletStatus;
import com.stablecoin.coreapi.generated.model.WalletVerificationRequest;
import com.stablecoin.coreapi.infra.repository.PostgresWalletsRepository;
import com.stablecoin.coreapi.models.WalletChallengeData;
import com.stablecoin.coreapi.util.AddressUtils;
import com.stablecoin.coreapi.util.Constants;
import com.stablecoin.coreapi.util.SignatureUtils;
import com.stablecoin.coreapi.web.error.ApiException;
import com.stablecoin.coreapi.web.error.ErrorCode;

@Service
public class WalletsService {

	@Value("${app.name}")
	private String appName;

	@Value("${app.domain}")
	private String domain;

	@Value("${app.URI}")
	private String uri;

	@Value("${app.chainId}")
	private String chainId;

	@Value("${app.challengeExpirationSeconds}")
	private int challengeExpirationSeconds;

	private static final SecureRandom secureRandom = new SecureRandom();
	private final PostgresWalletsRepository walletChallengeRepository;

	public WalletsService(PostgresWalletsRepository walletChallengeRepository) {
		this.walletChallengeRepository = walletChallengeRepository;
	}

	@Transactional
	public WalletChallenge generateWalletChallenge(WalletChallengeRequest request) {
		// Calculate the checksum address and generate a nonce
		String checksumAddress = AddressUtils.validateAndChecksumWalletAddress(request.getWalletAddress());
		String storedAddress = AddressUtils.normalizeAddress(request.getWalletAddress());
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

	/**
	 * Runs in one transaction: consuming the nonce and marking the wallet VERIFIED
	 * either both commit or both roll back (any RuntimeException, such as
	 * ApiException, triggers the rollback).
	 */
	@Transactional
	public Wallet verifyWalletSignature(WalletVerificationRequest request) {
		String checksumAddress = AddressUtils.validateAndChecksumWalletAddress(request.getWalletAddress());
		String storedAddress = AddressUtils.normalizeAddress(request.getWalletAddress());
		String nonce = request.getNonce();

		// retrieve the stored challenge and check it is unused and unexpired
		WalletChallengeData data = walletChallengeRepository.getWalletChallengeByAddressAndNonce(storedAddress, nonce)
				.orElseThrow(() -> new ApiException(ErrorCode.INVALID_NONCE,
						"No challenge found for nonce " + nonce + " and wallet " + checksumAddress));
		if (data.getUsedAt() != null) {
			throw new ApiException(ErrorCode.NONCE_ALREADY_USED,
					"Nonce " + nonce + " was already used at " + data.getUsedAt());
		}
		if (!data.getExpiresAt().isAfter(OffsetDateTime.now())) {
			throw new ApiException(ErrorCode.CHALLENGE_EXPIRED,
					"Challenge expired at " + data.getExpiresAt() + "; request a new one");
		}

		// recover the EIP-191 signer of the stored challenge message (checksum format)
		String signerAddress = recoverSigner(data.getMessage(), request.getSignature());
		validateSignerAddress(signerAddress, checksumAddress);
		validateNonce(nonce, data.getNonce());

		// consume the nonce; 0 rows updated means a concurrent request consumed it
		// first
		if (!walletChallengeRepository.markWalletChallengeUsed(nonce, storedAddress)) {
			throw new ApiException(ErrorCode.NONCE_ALREADY_USED, "Nonce " + nonce + " was already used");
		}
		OffsetDateTime verifiedAt = walletChallengeRepository.markWalletVerified(storedAddress);

		Wallet walletResponse = new Wallet();
		walletResponse.setWalletAddress(checksumAddress);
		walletResponse.setStatus(WalletStatus.VERIFIED);
		walletResponse.setVerifiedAt(verifiedAt);
		return walletResponse;
	}

	public Wallet getWallet(String address) {
		String storedAddress = AddressUtils.normalizeAddress(address);

		Optional<Wallet> walletOptional = walletChallengeRepository.getWalletByAddress(storedAddress);
		if (!walletOptional.isPresent()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Wallet not found");
		}
		return walletOptional.get().walletAddress(storedAddress);
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

	private void validateSignerAddress(String signerAddress, String requestWalletAddress) {
		if (!signerAddress.equalsIgnoreCase(requestWalletAddress)) {
			throw new ApiException(ErrorCode.INVALID_SIGNATURE, "Signature does not match the wallet address");
		}
	}

	private void validateNonce(String requestNonce, String storedNonce) {
		if (!requestNonce.equals(storedNonce)) {
			throw new ApiException(ErrorCode.INVALID_NONCE, "Nonce does not match the stored challenge");
		}
	}

	private String recoverSigner(String message, String signature) {
		try {
			return SignatureUtils.recoverPersonalSignSigner(message, signature);
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.INVALID_SIGNATURE, e.getMessage());
		}
	}
}
