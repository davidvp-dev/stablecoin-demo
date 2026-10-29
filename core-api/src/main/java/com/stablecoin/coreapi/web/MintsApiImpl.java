package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.MintsApi;
import com.stablecoin.coreapi.generated.model.CreatedOrderResponse;
import com.stablecoin.coreapi.generated.model.OrderDetails;
import com.stablecoin.coreapi.generated.model.WalletAmountRequest;
import com.stablecoin.coreapi.service.MintService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MintsApiImpl implements MintsApi {

	private final MintService mintService;

	public MintsApiImpl(MintService mintService) {
		this.mintService = mintService;
	}

	@Override
	public ResponseEntity<CreatedOrderResponse> createMintOrders(String idempotencyKey,
			WalletAmountRequest walletAmountRequest) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(mintService.createMintOrder(idempotencyKey, walletAmountRequest));
	}

	@Override
	public ResponseEntity<OrderDetails> getMintOrderById(String id) {
		return ResponseEntity.ok(mintService.getMintOrderById(id));
	}
}
