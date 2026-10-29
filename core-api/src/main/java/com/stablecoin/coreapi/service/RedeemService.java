package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.CreatedOrderResponse;
import com.stablecoin.coreapi.generated.model.OrderDetails;
import com.stablecoin.coreapi.generated.model.WalletAmountRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RedeemService {

	public CreatedOrderResponse createRedeemOrder(String idempotencyKey, WalletAmountRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Redeem order creation is not implemented");
	}

	public OrderDetails getRedeemOrderById(String id) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Redeem order lookup is not implemented");
	}
}
