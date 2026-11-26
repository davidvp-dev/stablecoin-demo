package com.stablecoin.chainservice.service;

/**
 * The RPC node could not be reached, or answered with an error. Mapped to 503
 * with code RPC_UNAVAILABLE (api-definition.yml).
 */
public class RpcUnavailableException extends RuntimeException {

	public RpcUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
