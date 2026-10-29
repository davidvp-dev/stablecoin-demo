package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.IncomingTransferResponse;
import com.stablecoin.coreapi.generated.model.WalletAmountRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankSimService {

	public IncomingTransferResponse simulateIncomingBankTransfer(WalletAmountRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Incoming bank transfers are not implemented");
	}
}
