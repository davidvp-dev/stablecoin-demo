package com.stablecoin.coreapi.models;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class WalletChallengeData {

    public String nonce;
    public String walletAddress;
    public String message;
    public OffsetDateTime expiresAt;
    public OffsetDateTime usedAt;
    public OffsetDateTime createdAt;

}
