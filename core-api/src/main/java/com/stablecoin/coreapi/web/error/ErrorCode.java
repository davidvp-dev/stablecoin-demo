package com.stablecoin.coreapi.web.error;

import org.springframework.http.HttpStatus;

/**
 * Stable business error codes, sent in the {@code code} field of every Problem
 * Details response.
 * The portal maps each code to a user message, so a code must never be renamed
 * once the portal uses it.
 * Each code carries the HTTP status and the short title of the response.
 */
public enum ErrorCode {

	// Generic errors, used by the global handler for Spring's own exceptions
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request"),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
	CONFLICT(HttpStatus.CONFLICT, "Conflict"),
	NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "Not implemented"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

	// Business errors: add the new ones here as each endpoint is implemented
	INVALID_WALLET_ADDRESS(HttpStatus.BAD_REQUEST, "Invalid wallet address"),
	INVALID_NONCE(HttpStatus.BAD_REQUEST, "Invalid nonce"),
	NONCE_ALREADY_USED(HttpStatus.UNPROCESSABLE_CONTENT, "Nonce already used"),
	CHALLENGE_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Challenge expired"),
	INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "Invalid signature"),
	WALLET_NOT_VERIFIED(HttpStatus.UNPROCESSABLE_CONTENT, "Wallet not verified"),
	IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key already used");

	private final HttpStatus status;
	private final String title;

	ErrorCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
	}

	public HttpStatus status() {
		return status;
	}

	public String title() {
		return title;
	}

	/**
	 * Generic code for an HTTP status, used when an exception only carries a
	 * status.
	 */
	public static ErrorCode fromStatus(int status) {
		return switch (status) {
			case 400 -> VALIDATION_ERROR;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 409 -> CONFLICT;
			case 501 -> NOT_IMPLEMENTED;
			default -> INTERNAL_ERROR;
		};
	}
}
