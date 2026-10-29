package com.stablecoin.coreapi.web;

import com.stablecoin.coreapi.generated.api.ReconciliationApi;
import com.stablecoin.coreapi.generated.model.ReconciliationSummary;
import com.stablecoin.coreapi.service.ReconciliationService;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconciliationApiImpl implements ReconciliationApi {

	private final ReconciliationService reconciliationService;

	public ReconciliationApiImpl(ReconciliationService reconciliationService) {
		this.reconciliationService = reconciliationService;
	}

	@Override
	public ResponseEntity<ReconciliationSummary> getReconciliationData(LocalDate startDate, LocalDate endDate) {
		return ResponseEntity.ok(reconciliationService.getReconciliationData(startDate, endDate));
	}
}
