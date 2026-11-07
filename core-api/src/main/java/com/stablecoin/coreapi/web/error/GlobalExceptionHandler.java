package com.stablecoin.coreapi.web.error;

import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception thrown by a controller into a Problem Details response (RFC 9457), with content type
 * {@code application/problem+json} and the shape of the {@code Problem} schema in api-definition.yml:
 *
 * <pre>
 * { "type": "about:blank", "title": "Invalid request", "status": 400, "detail": "...",
 *   "instance": "/api/mint-orders", "code": "VALIDATION_ERROR",
 *   "errors": [ { "field": "amountCents", "message": "must be greater than or equal to 100" } ] }
 * </pre>
 *
 * It extends {@link ResponseEntityExceptionHandler}, which already handles Spring MVC's own exceptions (malformed
 * JSON, missing header, unknown path, wrong method...) as Problem Details. This class adds the {@code code} field to
 * all of them and handles the exceptions specific to this application.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String CODE = "code";
	private static final String ERRORS = "errors";

	// ─── Application exceptions ───

	/** Business errors thrown by the services: status, title and code come from the {@link ErrorCode}. */
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, WebRequest request) {
		return problem(ex.getCode(), ex.getMessage(), request);
	}

	/**
	 * Constraint violations on parameters and headers, e.g. {@code @Size} on the Idempotency-Key header. The generated
	 * interfaces are annotated with {@code @Validated}, so Spring validates them with this exception.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
			WebRequest request) {
		List<Map<String, String>> errors = ex.getConstraintViolations().stream()
				.map(v -> fieldError(lastNode(v.getPropertyPath()), v.getMessage()))
				.toList();
		return validationProblem(errors, request);
	}

	/** Last resort: any unexpected exception becomes a 500 without leaking its message or stack trace. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Unexpected error on {}", path(request), ex);
		return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
	}

	// ─── Spring MVC exceptions with a more precise response than the default ───

	/** {@code @Valid} on a request body failed: list every invalid field. */
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> fieldError(e.getField(), e.getDefaultMessage()))
				.toList();
		return asObject(validationProblem(errors, request));
	}

	/** Built-in method validation failed on a parameter (when the controller is not proxied by {@code @Validated}). */
	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(e -> fieldError(result.getMethodParameter().getParameterName(), e.getDefaultMessage())))
				.toList();
		return asObject(validationProblem(errors, request));
	}

	/** The body is not valid JSON, or a value has the wrong type. Don't echo the parser's message. */
	@Override
	protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return asObject(problem(ErrorCode.MALFORMED_REQUEST, "The request body is missing or is not valid JSON",
				request));
	}

	/**
	 * Every other Spring MVC exception (missing header, unknown path, wrong method, ResponseStatusException...)
	 * goes through here. Spring builds the Problem Details body inside {@code super}, so call it first, then add the
	 * {@code code} and the {@code instance} so the response has the same shape as the rest.
	 */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
				problem.setProperty(CODE, ErrorCode.fromStatus(statusCode.value()).name());
			}
			if (problem.getInstance() == null) {
				problem.setInstance(java.net.URI.create(path(request)));
			}
		}
		return response;
	}

	// ─── Helpers ───

	private ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setTitle(code.title());
		problem.setInstance(java.net.URI.create(path(request)));
		problem.setProperty(CODE, code.name());
		return ResponseEntity.status(code.status()).body(problem);
	}

	private ResponseEntity<ProblemDetail> validationProblem(List<Map<String, String>> errors, WebRequest request) {
		ResponseEntity<ProblemDetail> response = problem(ErrorCode.VALIDATION_ERROR,
				"One or more fields are invalid", request);
		response.getBody().setProperty(ERRORS, errors);
		return response;
	}

	private static Map<String, String> fieldError(String field, @Nullable String message) {
		return Map.of("field", field, "message", message == null ? "is invalid" : message);
	}

	/** {@code createMintOrder.idempotencyKey} → {@code idempotencyKey}. */
	private static String lastNode(Path path) {
		String name = null;
		for (Path.Node node : path) {
			name = node.getName();
		}
		return name == null ? path.toString() : name;
	}

	/** Request path including the /api context path, e.g. {@code /api/mint-orders}. */
	private static String path(WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest) {
			return servletRequest.getRequest().getRequestURI();
		}
		return request.getDescription(false).replace("uri=", "");
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ResponseEntity<Object> asObject(ResponseEntity<ProblemDetail> response) {
		return (ResponseEntity) response;
	}
}
