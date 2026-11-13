package com.stablecoin.coreapi.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.stablecoin.coreapi.generated.api.MintOrdersApi;
import com.stablecoin.coreapi.generated.model.CreateMintOrderRequest;
import com.stablecoin.coreapi.generated.model.MintOrder;
import com.stablecoin.coreapi.generated.model.MintOrderDetail;
import com.stablecoin.coreapi.models.CreateMintOrderResult;
import com.stablecoin.coreapi.service.MintService;

@RestController
public class MintOrdersApiImpl implements MintOrdersApi {

	private final MintService mintService;

	public MintOrdersApiImpl(MintService mintService) {
		this.mintService = mintService;
	}

	@Override
	public ResponseEntity<MintOrder> createMintOrder(String idempotencyKey,
			CreateMintOrderRequest createMintOrderRequest) {
		CreateMintOrderResult result = mintService.createMintOrder(idempotencyKey, createMintOrderRequest);
		// 201 when this request created the order, 200 when it replays an earlier one
		// with the same Idempotency-Key. The body is the same order in both cases.
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.order());
	}

	@Override
	public ResponseEntity<MintOrderDetail> getMintOrder(UUID id) {
		return ResponseEntity.ok(mintService.getMintOrder(id));
	}
}
