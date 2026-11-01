package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.WalletsApi;
import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletChallenge;
import com.stablecoin.coreapi.generated.model.WalletChallengeRequest;
import com.stablecoin.coreapi.generated.model.WalletVerificationRequest;
import com.stablecoin.coreapi.service.WalletsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalletsApiImpl implements WalletsApi {

	private static final Logger log = LoggerFactory.getLogger(WalletsApiImpl.class);

	private final WalletsService walletsService;

	public WalletsApiImpl(WalletsService walletsService) {
		this.walletsService = walletsService;
	}

	@Override
	public ResponseEntity<WalletChallenge> generateWalletChallenge(WalletChallengeRequest walletChallengeRequest) {
		log.info("Generating wallet challenge for {}", walletChallengeRequest.getWalletAddress());
		return ResponseEntity.ok(walletsService.generateWalletChallenge(walletChallengeRequest));
	}

	@Override
	public ResponseEntity<Wallet> getWallet(String address) {
		log.info("Fetching wallet {}", address);
		return ResponseEntity.ok(walletsService.getWallet(address));
	}

	@Override
	public ResponseEntity<Wallet> verifyWalletSignature(WalletVerificationRequest walletVerificationRequest) {
		log.info("Verifying wallet signature for {}", walletVerificationRequest.getWalletAddress());
		return ResponseEntity.ok(walletsService.verifyWalletSignature(walletVerificationRequest));
	}
}
