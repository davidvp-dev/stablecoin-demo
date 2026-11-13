package com.stablecoin.coreapi.util;

import java.util.Locale;

import org.web3j.crypto.Keys;

import com.stablecoin.coreapi.web.error.ApiException;
import com.stablecoin.coreapi.web.error.ErrorCode;

public class AddressUtils {

    private AddressUtils() {
    }

    public static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        return address.toLowerCase(Locale.ROOT);
    }

    public static String validateAndChecksumWalletAddress(String address) {
        if (address == null || address.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_WALLET_ADDRESS, "Wallet address is required");
        }
        try {
            return Keys.toChecksumAddress(address); // Validate Ethereum address format
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_WALLET_ADDRESS, "Invalid Ethereum wallet address: " + address);
        }
    }
}
