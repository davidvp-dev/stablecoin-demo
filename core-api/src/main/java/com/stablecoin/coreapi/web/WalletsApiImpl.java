package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.WalletsApi;
import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletChallengeRequest;
import com.stablecoin.coreapi.generated.model.WalletChallengeResponse;
import com.stablecoin.coreapi.generated.model.WalletVerificationRequest;
import com.stablecoin.coreapi.generated.model.WalletVerificationResponse;
import com.stablecoin.coreapi.service.WalletsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalletsApiImpl implements WalletsApi {

	private final WalletsService walletsService;

	public WalletsApiImpl(WalletsService walletsService) {
		this.walletsService = walletsService;
	}

	@Override
	public ResponseEntity<WalletChallengeResponse> generateWalletChallenge(
			@Valid WalletChallengeRequest walletChallengeRequest) {
		System.out.println("Generating wallet challenge for request: {}" + walletChallengeRequest);
		WalletChallengeResponse response = walletsService.generateWalletChallenge(walletChallengeRequest);
		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Wallet> getWalletInfo(String address) {
		System.out.println("Fetching wallet info for address: " + address);
		return ResponseEntity.ok(walletsService.getWalletInfo(address));
	}

	@Override
	public ResponseEntity<WalletVerificationResponse> verifyWalletSignature(
			@Valid WalletVerificationRequest walletVerificationRequest) {
		System.out.println("Verifying wallet signature for request: " + walletVerificationRequest);
		return ResponseEntity.ok(walletsService.verifyWalletSignature(walletVerificationRequest));
	}
}
