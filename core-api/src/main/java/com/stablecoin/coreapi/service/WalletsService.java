package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletChallengeRequest;
import com.stablecoin.coreapi.generated.model.WalletChallengeResponse;
import com.stablecoin.coreapi.generated.model.WalletVerificationRequest;
import com.stablecoin.coreapi.generated.model.WalletVerificationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletsService {

	public WalletChallengeResponse generateWalletChallenge(WalletChallengeRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Wallet challenges are not implemented");
	}

	public Wallet getWalletInfo(String address) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Wallet lookup is not implemented");
	}

	public WalletVerificationResponse verifyWalletSignature(WalletVerificationRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Wallet signature verification is not implemented");
	}
}
