package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.CreateRedeemOrderRequest;
import com.stablecoin.coreapi.generated.model.RedeemOrder;
import com.stablecoin.coreapi.generated.model.RedeemOrderDetail;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RedeemService {

	public RedeemOrder createRedeemOrder(String idempotencyKey, CreateRedeemOrderRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Redeem order creation is not implemented");
	}

	public RedeemOrderDetail getRedeemOrder(UUID id) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Redeem order lookup is not implemented");
	}
}
