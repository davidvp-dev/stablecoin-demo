package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.OrdersApi;
import com.stablecoin.coreapi.generated.model.OrderSummary;
import com.stablecoin.coreapi.generated.model.OrderType;
import com.stablecoin.coreapi.service.OrdersService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order history. The SSE stream (GET /orders/stream) is not part of this interface: it will be a
 * hand-written SseEmitter controller, because the generated OrdersStreamApi cannot return a stream.
 */
@RestController
public class OrdersApiImpl implements OrdersApi {

	private final OrdersService ordersService;

	public OrdersApiImpl(OrdersService ordersService) {
		this.ordersService = ordersService;
	}

	@Override
	public ResponseEntity<List<OrderSummary>> listOrders(String wallet, OrderType type, String status) {
		return ResponseEntity.ok(ordersService.listOrders(wallet, type, status));
	}
}
