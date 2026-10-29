package com.stablecoin.coreapi.service;

import com.stablecoin.coreapi.generated.model.ReconciliationSummary;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReconciliationService {

	public ReconciliationSummary getReconciliationData(LocalDate startDate, LocalDate endDate) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Reconciliation is not implemented");
	}
}
