package com.stablecoin.coreapi.web.error;

/**
 * Exception the services throw for a business error, for example
 * {@code throw new ApiException(ErrorCode.WALLET_NOT_VERIFIED, "The wallet " + address + " must be verified first")}.
 * {@link GlobalExceptionHandler} turns it into a Problem Details response with the code's status and title.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	public ApiException(ErrorCode code, String detail) {
		super(detail);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
