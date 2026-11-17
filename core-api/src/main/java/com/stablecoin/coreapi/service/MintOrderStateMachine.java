package com.stablecoin.coreapi.service;

import static com.stablecoin.coreapi.generated.model.MintOrderStatus.APPROVED;
import static com.stablecoin.coreapi.generated.model.MintOrderStatus.COMPLETED;
import static com.stablecoin.coreapi.generated.model.MintOrderStatus.CREATED;
import static com.stablecoin.coreapi.generated.model.MintOrderStatus.EXPIRED;
import static com.stablecoin.coreapi.generated.model.MintOrderStatus.FAILED;
import static com.stablecoin.coreapi.generated.model.MintOrderStatus.PAID;
import static com.stablecoin.coreapi.generated.model.MintOrderStatus.REJECTED;
import static com.stablecoin.coreapi.generated.model.MintOrderStatus.SUBMITTED;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.stablecoin.coreapi.generated.model.MintOrderStatus;
import com.stablecoin.coreapi.infra.repository.PostgresMintOrdersRepository;
import com.stablecoin.coreapi.models.MintOrderData;

/**
 * The only place that changes a mint order's status. It lists the allowed
 * changes (docs/architecture.md, "Mint order") and writes every attempt to
 * order_state_transitions: applied = true when the change happens, false when it
 * is rejected (RF-CORE-20, 22).
 */
@Component
public class MintOrderStateMachine {

	private static final Logger log = LoggerFactory.getLogger(MintOrderStateMachine.class);

	/**
	 * APPROVED → COMPLETED is not in the architecture diagram: it covers a lost
	 * MintSubmitted, when MintConfirmed arrives while the order is still APPROVED
	 * (IMPLEMENTATION_PLAN_V1, Day 4).
	 */
	private static final Map<MintOrderStatus, Set<MintOrderStatus>> ALLOWED = Map.of(
			CREATED, Set.of(PAID, REJECTED, EXPIRED),
			PAID, Set.of(APPROVED),
			APPROVED, Set.of(SUBMITTED, FAILED, COMPLETED),
			SUBMITTED, Set.of(COMPLETED, FAILED));

	private final PostgresMintOrdersRepository mintOrdersRepository;

	public MintOrderStateMachine(PostgresMintOrdersRepository mintOrdersRepository) {
		this.mintOrdersRepository = mintOrdersRepository;
	}

	public static boolean isAllowed(MintOrderStatus from, MintOrderStatus to) {
		return ALLOWED.getOrDefault(from, Set.of()).contains(to);
	}

	/**
	 * Moves the order to {@code to}. Must run inside the caller's transaction.
	 * On success it also updates {@code order}'s status.
	 *
	 * @param failureReason stored in mint_orders.failure_reason, or null to keep
	 *                      the current value
	 * @return false if the change is not allowed, or the order is no longer in
	 *         the status {@code order} holds; the attempt is recorded with
	 *         applied = false and the order is left as it was
	 */
	public boolean transition(MintOrderData order, MintOrderStatus to, String cause, String failureReason) {
		MintOrderStatus from = order.getStatus();
		boolean applied = isAllowed(from, to)
				&& mintOrdersRepository.updateMintOrderStatus(order.getId(), from, to, failureReason);

		mintOrdersRepository.insertMintOrderStateTransition(order.getId(), from.getValue(), to.getValue(), cause,
				applied);

		if (applied) {
			order.setStatus(to);
		} else {
			log.warn("Rejected transition of mint order {}: {} -> {} ({})", order.getId(), from, to, cause);
		}
		return applied;
	}
}
