package com.stablecoin.coreapi.models;

import lombok.Data;

@Data
public class ERC4361 {

    public String domain;
    public String address;
    public String uri;
    public String version;
    public String chainId;
    public String nonce;
    public String issuedAt;
    public String expirationTime;
}
