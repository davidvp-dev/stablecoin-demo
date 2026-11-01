package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.OrderSummary;
import com.stablecoin.coreapi.generated.model.OrderType;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrdersService {

	public List<OrderSummary> listOrders(String wallet, OrderType type, String status) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Order listing is not implemented");
	}
}
