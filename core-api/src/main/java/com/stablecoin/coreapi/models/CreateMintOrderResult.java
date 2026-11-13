package com.stablecoin.coreapi.models;

import com.stablecoin.coreapi.generated.model.MintOrder;

/**
 * Result of POST /mint-orders. {@code created} is true when this request
 * inserted the order (201 Created) and false when it is a replay of an earlier
 * request with the same Idempotency-Key (200 OK).
 */
public record CreateMintOrderResult(MintOrder order, boolean created) {

    public static CreateMintOrderResult created(MintOrder order) {
        return new CreateMintOrderResult(order, true);
    }

    public static CreateMintOrderResult replayed(MintOrder order) {
        return new CreateMintOrderResult(order, false);
    }
}
