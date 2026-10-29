package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.CreatedOrderResponse;
import com.stablecoin.coreapi.generated.model.OrderDetails;
import com.stablecoin.coreapi.generated.model.WalletAmountRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MintService {

	public CreatedOrderResponse createMintOrder(String idempotencyKey, WalletAmountRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Mint order creation is not implemented");
	}

	public OrderDetails getMintOrderById(String id) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Mint order lookup is not implemented");
	}
}
