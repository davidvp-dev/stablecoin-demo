package com.stablecoin.chainservice.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.stablecoin.chainservice.service.RpcUnavailableException;

/**
 * Problem Details responses (RFC 9457) with the {@code code} field of the
 * Problem schema in api-definition.yml.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(RpcUnavailableException.class)
	public ResponseEntity<ProblemDetail> handleRpcUnavailable(RpcUnavailableException ex) {
		log.warn("{}", ex.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "RPC node not reachable", ex.getMessage(), "RPC_UNAVAILABLE");
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
		HttpStatusCode status = ex.getStatusCode();
		String code = status.value() == 501 ? "NOT_IMPLEMENTED" : "ERROR";
		return problem(status, ex.getReason(), ex.getReason(), code);
	}

	/** Anything unexpected: 500 without leaking the message. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred",
				"INTERNAL_ERROR");
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String title, String detail,
			String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return ResponseEntity.status(status).body(problem);
	}
}
