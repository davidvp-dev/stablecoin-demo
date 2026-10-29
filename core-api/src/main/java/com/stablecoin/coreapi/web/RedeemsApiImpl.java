package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.RedeemsApi;
import com.stablecoin.coreapi.generated.model.CreatedOrderResponse;
import com.stablecoin.coreapi.generated.model.OrderDetails;
import com.stablecoin.coreapi.generated.model.WalletAmountRequest;
import com.stablecoin.coreapi.service.RedeemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedeemsApiImpl implements RedeemsApi {

	private final RedeemService redeemService;

	public RedeemsApiImpl(RedeemService redeemService) {
		this.redeemService = redeemService;
	}

	@Override
	public ResponseEntity<CreatedOrderResponse> createRedeemOrder(String idempotencyKey,
			WalletAmountRequest walletAmountRequest) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(redeemService.createRedeemOrder(idempotencyKey, walletAmountRequest));
	}

	@Override
	public ResponseEntity<OrderDetails> getRedeemOrderById(String id) {
		return ResponseEntity.ok(redeemService.getRedeemOrderById(id));
	}
}
