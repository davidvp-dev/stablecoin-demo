package com.stablecoin.coreapi.models;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.stablecoin.coreapi.generated.model.MintOrderStatus;

import lombok.Data;

/**
 * One row of the mint_orders table. Unlike the generated MintOrder (the API
 * response), it also carries the internal columns idempotency_key,
 * request_hash and updated_at.
 */
@Data
public class MintOrderData {

    public UUID id;
    public String walletAddress;
    public Long amountCents;
    public String sepaReference;
    public MintOrderStatus status;
    public String idempotencyKey;
    public String requestHash;
    public String txHash;
    public String failureReason;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public OffsetDateTime expiresAt;
}
