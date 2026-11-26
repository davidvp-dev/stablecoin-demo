package com.stablecoin.chainservice.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.stablecoin.chainservice.generated.api.ChainApi;
import com.stablecoin.chainservice.generated.model.ChainHealth;
import com.stablecoin.chainservice.generated.model.HealthStatus;
import com.stablecoin.chainservice.generated.model.Supply;
import com.stablecoin.chainservice.service.ChainHealthService;

@RestController
public class ChainApiImpl implements ChainApi {

	private final ChainHealthService chainHealthService;

	public ChainApiImpl(ChainHealthService chainHealthService) {
		this.chainHealthService = chainHealthService;
	}

	/**
	 * 200 when every check passes, 503 with the same body when one fails, so a
	 * monitor or a Docker healthcheck can rely on the status code alone.
	 */
	@Override
	public ResponseEntity<ChainHealth> getHealth() {
		ChainHealth health = chainHealthService.getHealth();
		HttpStatus status = health.getStatus() == HealthStatus.UP ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(status).body(health);
	}

	/** Day 6 (IMPLEMENTATION_PLAN_V1). */
	@Override
	public ResponseEntity<Supply> getSupply() {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Supply is not implemented yet");
	}
}
