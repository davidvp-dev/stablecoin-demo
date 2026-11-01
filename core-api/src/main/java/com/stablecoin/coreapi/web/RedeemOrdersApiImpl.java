package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.RedeemOrdersApi;
import com.stablecoin.coreapi.generated.model.CreateRedeemOrderRequest;
import com.stablecoin.coreapi.generated.model.RedeemOrder;
import com.stablecoin.coreapi.generated.model.RedeemOrderDetail;
import com.stablecoin.coreapi.service.RedeemService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedeemOrdersApiImpl implements RedeemOrdersApi {

	private final RedeemService redeemService;

	public RedeemOrdersApiImpl(RedeemService redeemService) {
		this.redeemService = redeemService;
	}

	@Override
	public ResponseEntity<RedeemOrder> createRedeemOrder(String idempotencyKey,
			CreateRedeemOrderRequest createRedeemOrderRequest) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(redeemService.createRedeemOrder(idempotencyKey, createRedeemOrderRequest));
	}

	@Override
	public ResponseEntity<RedeemOrderDetail> getRedeemOrder(UUID id) {
		return ResponseEntity.ok(redeemService.getRedeemOrder(id));
	}
}
