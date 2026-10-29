package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.OrdersApi;
import com.stablecoin.coreapi.generated.model.OrderSummary;
import com.stablecoin.coreapi.service.OrdersService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrdersApiImpl implements OrdersApi {

	private final OrdersService ordersService;

	public OrdersApiImpl(OrdersService ordersService) {
		this.ordersService = ordersService;
	}

	@Override
	public ResponseEntity<List<OrderSummary>> getAllOrders(String walletAddress) {
		return ResponseEntity.ok(ordersService.getAllOrders(walletAddress));
	}

	@Override
	public ResponseEntity<OrderSummary> streamAllOrders(String walletAddress) {
		return ResponseEntity.ok(ordersService.streamAllOrders(walletAddress));
	}
}
