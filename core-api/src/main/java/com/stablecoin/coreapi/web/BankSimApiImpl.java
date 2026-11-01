package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.BankSimApi;
import com.stablecoin.coreapi.generated.model.BankTransfer;
import com.stablecoin.coreapi.generated.model.IncomingTransferRequest;
import com.stablecoin.coreapi.service.BankSimService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BankSimApiImpl implements BankSimApi {

	private final BankSimService bankSimService;

	public BankSimApiImpl(BankSimService bankSimService) {
		this.bankSimService = bankSimService;
	}

	@Override
	public ResponseEntity<BankTransfer> createIncomingTransfer(IncomingTransferRequest incomingTransferRequest) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(bankSimService.registerIncomingTransfer(incomingTransferRequest));
	}
}
