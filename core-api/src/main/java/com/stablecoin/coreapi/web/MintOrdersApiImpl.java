package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.MintOrdersApi;
import com.stablecoin.coreapi.generated.model.CreateMintOrderRequest;
import com.stablecoin.coreapi.generated.model.MintOrder;
import com.stablecoin.coreapi.generated.model.MintOrderDetail;
import com.stablecoin.coreapi.service.MintService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MintOrdersApiImpl implements MintOrdersApi {

	private final MintService mintService;

	public MintOrdersApiImpl(MintService mintService) {
		this.mintService = mintService;
	}

	@Override
	public ResponseEntity<MintOrder> createMintOrder(String idempotencyKey,
			CreateMintOrderRequest createMintOrderRequest) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(mintService.createMintOrder(idempotencyKey, createMintOrderRequest));
	}

	@Override
	public ResponseEntity<MintOrderDetail> getMintOrder(UUID id) {
		return ResponseEntity.ok(mintService.getMintOrder(id));
	}
}
