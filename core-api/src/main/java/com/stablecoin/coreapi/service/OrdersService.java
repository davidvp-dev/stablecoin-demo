package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.OrderSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrdersService {

	public List<OrderSummary> getAllOrders(String walletAddress) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Order listing is not implemented");
	}

	public OrderSummary streamAllOrders(String walletAddress) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Order streaming is not implemented");
	}
}
