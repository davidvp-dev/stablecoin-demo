package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.BankTransfer;
import com.stablecoin.coreapi.generated.model.IncomingTransferRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankSimService {

	public BankTransfer registerIncomingTransfer(IncomingTransferRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Incoming bank transfers are not implemented");
	}
}
