package com.stablecoin.coreapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.web3j.crypto.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.stablecoin.coreapi.generated.model.CreateMintOrderRequest;
import com.stablecoin.coreapi.generated.model.MintOrder;
import com.stablecoin.coreapi.generated.model.MintOrderDetail;
import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletStatus;
import com.stablecoin.coreapi.infra.repository.PostgresMintOrdersRepository;
import com.stablecoin.coreapi.infra.repository.PostgresWalletsRepository;
import com.stablecoin.coreapi.models.CreateMintOrderResult;
import com.stablecoin.coreapi.models.MintOrderData;
import com.stablecoin.coreapi.util.AddressUtils;
import com.stablecoin.coreapi.web.error.ApiException;
import com.stablecoin.coreapi.web.error.ErrorCode;

import jakarta.transaction.Transactional;

@Service
public class MintService {

	private final PostgresMintOrdersRepository mintOrdersRepository;
	private final PostgresWalletsRepository walletsRepository;
	private static final SecureRandom secureRandom = new SecureRandom();

	@Value("${app.issuerIban}")
	private String issuerIban;

	@Value("${app.sepaReferencePrefix}")
	private String sepaReferencePrefix;

	public MintService(PostgresMintOrdersRepository mintOrdersRepository, PostgresWalletsRepository walletsRepository) {
		this.mintOrdersRepository = mintOrdersRepository;
		this.walletsRepository = walletsRepository;
	}

	// Day 2 of IMPLEMENTATION_PLAN_V1 (CU-02, RF-CORE-04, 05).
	// Mark the method @Transactional: the order row and its first state transition
	// (steps 6-7) must be saved together.
	@Transactional
	public CreateMintOrderResult createMintOrder(String idempotencyKey, CreateMintOrderRequest request) {
		String walletAddress = AddressUtils.normalizeAddress(request.getWalletAddress());
		Long amountCents = request.getAmountCents();
		String amountCentsStr = amountCents.toString();

		// 2. Compute request_hash: SHA-256 (hex, 64 chars) of the normalized request,
		String input = walletAddress + ":" + amountCentsStr;
		String requestHash = "";
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(input.getBytes(StandardCharsets.UTF_8));
			requestHash = HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new ApiException(ErrorCode.INTERNAL_ERROR, "SHA-256 algorithm not available");
		}

		// Check if the idempotency key has been used before. If so, check if the
		// request_hash matches.
		Optional<MintOrderData> existingOrder = mintOrdersRepository.findMintOrderByIdempotencyKey(idempotencyKey);
		if (existingOrder.isPresent()) {
			MintOrderData orderData = existingOrder.get();
			if (orderData.getRequestHash().equals(requestHash)) {
				// Replay: return the existing order (the API answers 200, not 201).
				return CreateMintOrderResult.replayed(toMintOrder(orderData));
			} else {
				throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
						"Idempotency key reused with different request");
			}
		}

		// 4. Check the wallet: it must exist in `wallets` with status VERIFIED,
		Optional<Wallet> wallet = walletsRepository.getWalletByAddress(walletAddress);
		if (!(wallet.isPresent() && wallet.get().getStatus().equals(WalletStatus.VERIFIED))) {
			throw new ApiException(ErrorCode.WALLET_NOT_VERIFIED, "Wallet is not verified");
		}

		// 5. Build the new order values:
		String sepaReference = sepaReferencePrefix + generateRandomAlphanumeric(8);
		MintOrderData mintOrderData = new MintOrderData();
		mintOrderData.setWalletAddress(walletAddress);
		mintOrderData.setAmountCents(amountCents);
		mintOrderData.setSepaReference(sepaReference);
		mintOrderData.setIdempotencyKey(idempotencyKey);
		mintOrderData.setRequestHash(requestHash);
		mintOrderData.setExpiresAt(java.time.OffsetDateTime.now().plusHours(24));

		// 6. INSERT into mint_orders (wallet_address, amount_cents, sepa_reference,
		UUID orderId = mintOrdersRepository.insertMintOrder(mintOrderData);

		// 7. INSERT the initial row in order_state_transitions:
		mintOrdersRepository.insertMintOrderStateTransition(orderId,
				null, "CREATED", "CreateMintOrder");

		// 8. Map to MintOrder. insertMintOrder already copied id, status and created_at
		// into mintOrderData. created = true, so the controller answers 201.
		return CreateMintOrderResult.created(toMintOrder(mintOrderData));
	}

	/**
	 * Maps a mint_orders row to the API response. Used by both the new-order and
	 * the replay paths, so a replay returns exactly the same body as the first
	 * call.
	 */
	private MintOrder toMintOrder(MintOrderData orderData) {
		MintOrder mintOrder = new MintOrder();
		mintOrder.setId(orderData.getId());
		mintOrder.setWalletAddress(Keys.toChecksumAddress(orderData.getWalletAddress()));
		mintOrder.setAmountCents(orderData.getAmountCents());
		mintOrder.setStatus(orderData.getStatus());
		mintOrder.setSepaReference(orderData.getSepaReference());
		mintOrder.setIssuerIban(issuerIban);
		mintOrder.setTxHash(orderData.getTxHash());
		mintOrder.setFailureReason(orderData.getFailureReason());
		mintOrder.setCreatedAt(orderData.getCreatedAt());
		mintOrder.setExpiresAt(orderData.getExpiresAt());
		return mintOrder;
	}

	public MintOrderDetail getMintOrder(UUID id) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Mint order lookup is not implemented");
	}

	private static String generateRandomAlphanumeric(int length) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			int index = secureRandom.nextInt(chars.length());
			sb.append(chars.charAt(index));
		}
		return sb.toString();
	}
}
