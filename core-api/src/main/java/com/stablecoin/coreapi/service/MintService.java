package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.CreateMintOrderRequest;
import com.stablecoin.coreapi.generated.model.MintOrder;
import com.stablecoin.coreapi.generated.model.MintOrderDetail;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MintService {

	public MintOrder createMintOrder(String idempotencyKey, CreateMintOrderRequest request) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Mint order creation is not implemented");
	}

	public MintOrderDetail getMintOrder(UUID id) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Mint order lookup is not implemented");
	}
}
