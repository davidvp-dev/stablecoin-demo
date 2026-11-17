package com.stablecoin.coreapi.service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stablecoin.coreapi.generated.model.BankTransfer;
import com.stablecoin.coreapi.generated.model.IncomingTransferRequest;
import com.stablecoin.coreapi.generated.model.MintOrderStatus;
import com.stablecoin.coreapi.generated.model.TransferDirection;
import com.stablecoin.coreapi.generated.model.TransferOutcome;
import com.stablecoin.coreapi.infra.repository.PostgresBankTransfersRepository;
import com.stablecoin.coreapi.infra.repository.PostgresMintOrdersRepository;
import com.stablecoin.coreapi.models.MintOrderData;

/**
 * Simulated bank (CU-03, RF-CORE-06, 07, 08). Every incoming transfer is
 * recorded, whether or not it pays an order.
 */
@Service
public class BankSimService {

	private static final Logger log = LoggerFactory.getLogger(BankSimService.class);

	static final String CAUSE_INCOMING_TRANSFER = "IncomingTransfer";
	static final String CAUSE_AUTO_APPROVAL = "AutoApproval";
	static final String CAUSE_AMOUNT_MISMATCH = "AmountMismatch";

	/** Outgoing outcome for the money sent back when the amount is wrong. */
	private static final String OUTCOME_REFUND = "REFUND";

	private final PostgresMintOrdersRepository mintOrdersRepository;
	private final PostgresBankTransfersRepository bankTransfersRepository;
	private final MintOrderStateMachine stateMachine;
	private final OutboxService outboxService;

	public BankSimService(PostgresMintOrdersRepository mintOrdersRepository,
			PostgresBankTransfersRepository bankTransfersRepository, MintOrderStateMachine stateMachine,
			OutboxService outboxService) {
		this.mintOrdersRepository = mintOrdersRepository;
		this.bankTransfersRepository = bankTransfersRepository;
		this.stateMachine = stateMachine;
		this.outboxService = outboxService;
	}

	/**
	 * One transaction for everything: the transfer, the status changes and the
	 * MintApproved outbox row are saved together or not at all. The order row is
	 * locked first, so two transfers with the same reference can't both pay it.
	 */
	@Transactional
	public BankTransfer registerIncomingTransfer(IncomingTransferRequest request) {
		String reference = request.getReference().trim();
		long amountCents = request.getAmountCents();
		String senderIban = request.getSenderIban();

		// Our references are upper case; a bank may change the case, so match
		// ignoring it. The transfer keeps the reference as it was received.
		Optional<MintOrderData> match = mintOrdersRepository
				.findMintOrderBySepaReferenceForUpdate(reference.toUpperCase(Locale.ROOT));

		if (match.isEmpty()) {
			return unassigned(reference, amountCents, senderIban, "no order with this reference");
		}
		MintOrderData order = match.get();

		// Only a CREATED order that hasn't expired can be paid. A second payment of
		// a paid order, or one that arrives after expiry, stays UNASSIGNED for
		// manual handling: bank_transfers allows one incoming transfer per order.
		if (order.getStatus() != MintOrderStatus.CREATED) {
			return unassigned(reference, amountCents, senderIban,
					"order " + order.getId() + " is " + order.getStatus());
		}
		if (!order.getExpiresAt().isAfter(OffsetDateTime.now())) {
			return unassigned(reference, amountCents, senderIban, "order " + order.getId() + " has expired");
		}

		if (amountCents == order.getAmountCents()) {
			return pay(order, reference, amountCents, senderIban);
		}
		return reject(order, reference, amountCents, senderIban);
	}

	/** Right amount: CREATED → PAID → APPROVED, and MintApproved to the outbox. */
	private BankTransfer pay(MintOrderData order, String reference, long amountCents, String senderIban) {
		BankTransfer transfer = bankTransfersRepository.insertBankTransfer(TransferDirection.INCOMING, reference,
				amountCents, senderIban, TransferOutcome.MATCHED.getValue(), order.getId());

		apply(order, MintOrderStatus.PAID, CAUSE_INCOMING_TRANSFER, null);
		apply(order, MintOrderStatus.APPROVED, CAUSE_AUTO_APPROVAL, null);
		outboxService.enqueueMintApproved(order);

		transfer.setOutcome(TransferOutcome.MATCHED);
		return transfer;
	}

	/** Wrong amount: CREATED → REJECTED, and the money goes back to the sender. */
	private BankTransfer reject(MintOrderData order, String reference, long amountCents, String senderIban) {
		BankTransfer transfer = bankTransfersRepository.insertBankTransfer(TransferDirection.INCOMING, reference,
				amountCents, senderIban, TransferOutcome.REJECTED.getValue(), order.getId());

		String reason = "Amount mismatch: expected " + order.getAmountCents() + " cents, received " + amountCents
				+ " cents; refunded";
		apply(order, MintOrderStatus.REJECTED, CAUSE_AMOUNT_MISMATCH, reason);

		// Simulated refund of the whole amount received, to the account it came from.
		bankTransfersRepository.insertBankTransfer(TransferDirection.OUTGOING, reference, amountCents, senderIban,
				OUTCOME_REFUND, order.getId());

		transfer.setOutcome(TransferOutcome.REJECTED);
		return transfer;
	}

	private BankTransfer unassigned(String reference, long amountCents, String senderIban, String why) {
		log.info("Incoming transfer with reference '{}' left unassigned: {}", reference, why);
		BankTransfer transfer = bankTransfersRepository.insertBankTransfer(TransferDirection.INCOMING, reference,
				amountCents, senderIban, TransferOutcome.UNASSIGNED.getValue(), null);
		transfer.setOutcome(TransferOutcome.UNASSIGNED);
		return transfer;
	}

	/**
	 * The order row is locked and was just checked to be CREATED, so a rejected
	 * transition here is a bug: fail and roll the whole transfer back.
	 */
	private void apply(MintOrderData order, MintOrderStatus to, String cause, String failureReason) {
		if (!stateMachine.transition(order, to, cause, failureReason)) {
			throw new IllegalStateException(
					"Mint order " + order.getId() + " could not move from " + order.getStatus() + " to " + to);
		}
	}
}
