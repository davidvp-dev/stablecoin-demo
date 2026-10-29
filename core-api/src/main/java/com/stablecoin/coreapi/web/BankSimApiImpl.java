package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.BankSimApi;
import com.stablecoin.coreapi.generated.model.IncomingTransferResponse;
import com.stablecoin.coreapi.generated.model.WalletAmountRequest;
import com.stablecoin.coreapi.service.BankSimService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BankSimApiImpl implements BankSimApi {

	private final BankSimService bankSimService;

	public BankSimApiImpl(BankSimService bankSimService) {
		this.bankSimService = bankSimService;
	}

	@Override
	public ResponseEntity<IncomingTransferResponse> simulateIncomingBankTransfer(
			WalletAmountRequest walletAmountRequest) {
		return ResponseEntity.ok(bankSimService.simulateIncomingBankTransfer(walletAmountRequest));
	}
}
