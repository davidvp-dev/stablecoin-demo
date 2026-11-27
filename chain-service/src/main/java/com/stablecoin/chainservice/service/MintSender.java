package com.stablecoin.chainservice.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;

import com.stablecoin.chainservice.config.ChainProperties;
import com.stablecoin.chainservice.config.MintProperties;
import com.stablecoin.chainservice.generated.contracts.EURDemo;
import com.stablecoin.chainservice.infra.repository.PostgresSentTransactionsRepository;
import com.stablecoin.chainservice.models.SentTransaction;
import com.stablecoin.chainservice.util.Constants;
import com.stablecoin.chainservice.util.Conversions;
import com.stablecoin.chainservice.util.RevertDecoder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a MintApproved into one mint() transaction (CU-04, RF-CHAIN-01, 04..06).
 *
 * Three layers keep an order from being minted twice:
 * 1. sent_transactions has one row per order id. A redelivered MintApproved
 *    finds the row and only republishes the outcome it already had.
 * 2. Before sending, the hash of the signed transaction is saved. After a crash,
 *    the next attempt looks that hash up on the node before signing anything new.
 * 3. The contract itself rejects a second mint of the same bytes32
 *    (MintOrderAlreadySubmitted), checked with isMintOrderSubmitted() and with a
 *    simulation (eth_call) of every mint before it is sent.
 *
 * Failures come in two kinds:
 * - Business: the node rejects the transaction, or mint() would revert (paused,
 *   blacklisted recipient...). Retried app.mint.maxAttempts times with a fixed
 *   wait; then the row is FAILED and MintFailed is published.
 * - Infrastructure: the node or the database is unreachable. Thrown up to the
 *   listener, whose error handler redelivers the message until it works. They
 *   don't count as attempts: a node outage must not fail a paid order.
 */
@Service
public class MintSender {

	private static final Logger log = LoggerFactory.getLogger(MintSender.class);

	/** The parts of the MintApproved envelope this service reads. */
	record MintApprovedMessage(UUID eventId, String eventType, Integer version, UUID orderId, Payload payload) {
		record Payload(String wallet, Long amountCents) {
		}
	}

	/** A business failure of one attempt; its message becomes the failure reason. */
	static class MintAttemptException extends Exception {
		MintAttemptException(String message) {
			super(message);
		}
	}

	/**
	 * The simulation says the contract already has this order id: an earlier
	 * attempt was mined between the alreadySent() check and the simulation.
	 */
	static class AlreadyMintedException extends MintAttemptException {
		AlreadyMintedException() {
			super("order already minted on-chain");
		}
	}

	private final Web3j web3j;
	private final EURDemo eurDemo;
	private final RawTransactionManager transactionManager;
	private final Credentials minterCredentials;
	private final ChainProperties chainProperties;
	private final MintProperties mintProperties;
	private final PostgresSentTransactionsRepository sentTransactions;
	private final ChainEventPublisher eventPublisher;
	private final ObjectMapper objectMapper;

	public MintSender(Web3j web3j, EURDemo eurDemo, RawTransactionManager minterTransactionManager,
			Credentials minterCredentials, ChainProperties chainProperties, MintProperties mintProperties,
			PostgresSentTransactionsRepository sentTransactions, ChainEventPublisher eventPublisher,
			ObjectMapper objectMapper) {
		this.web3j = web3j;
		this.eurDemo = eurDemo;
		this.transactionManager = minterTransactionManager;
		this.minterCredentials = minterCredentials;
		this.chainProperties = chainProperties;
		this.mintProperties = mintProperties;
		this.sentTransactions = sentTransactions;
		this.eventPublisher = eventPublisher;
		this.objectMapper = objectMapper;
	}

	/**
	 * Entry point for one mint-orders.v1 message.
	 *
	 * @throws RpcUnavailableException and database / Kafka exceptions: the
	 *                                 listener retries the message
	 */
	public void handleMintApproved(String json) {
		Optional<MintApprovedMessage> parsed = parse(json);
		if (parsed.isEmpty()) {
			return; // logged in parse(); retrying a malformed message would never fix it
		}
		MintApprovedMessage message = parsed.get();
		UUID orderId = message.orderId();
		String wallet = message.payload().wallet().toLowerCase(Locale.ROOT);
		BigInteger units = Conversions.centsToUnits(message.payload().amountCents());

		sentTransactions.insertPendingIfAbsent(orderId, Conversions.toHex(Conversions.orderIdToBytes32(orderId)),
				chainProperties.chainId(), minterCredentials.getAddress().toLowerCase(Locale.ROOT), wallet, units);
		SentTransaction row = sentTransactions.findByOrderId(orderId).orElseThrow();

		if (!row.toAddress().equals(wallet) || !row.amountUnits().equals(units)) {
			// Same order id with different data: core-api never does this. The row is
			// what was (or will be) minted, so it wins; the event is only logged.
			log.error("MintApproved {} for order {} differs from the stored mint ({} units to {}); using the stored one",
					message.eventId(), orderId, row.amountUnits(), row.toAddress());
		}

		switch (row.status()) {
			case SentTransaction.SUBMITTED, SentTransaction.CONFIRMED -> {
				// Redelivery after the mint was sent: publish the outcome again. Same
				// deterministic eventId, so core-api skips it if it already has it.
				log.info("Order {} already {}, republishing MintSubmitted", orderId, row.status());
				publishSubmitted(row);
			}
			case SentTransaction.FAILED -> {
				log.info("Order {} already FAILED, republishing MintFailed", orderId);
				eventPublisher.publishMintFailed(orderId, row.failureReason(), row.txHash());
			}
			default -> mint(row);
		}
	}

	private void mint(SentTransaction row) {
		UUID orderId = row.orderId();
		byte[] onchainId = Conversions.orderIdToBytes32(orderId);

		if (alreadySent(row, onchainId)) {
			sentTransactions.markSubmitted(orderId);
			publishSubmitted(sentTransactions.findByOrderId(orderId).orElseThrow());
			return;
		}

		String callData = eurDemo.mint(row.toAddress(), row.amountUnits(), onchainId).encodeFunctionCall();
		while (true) {
			try {
				SentTransaction sent = attempt(row, callData);
				sentTransactions.markSubmitted(orderId);
				log.info("Mint of order {} submitted: tx {} nonce {}", orderId, sent.txHash(), sent.nonce());
				publishSubmitted(sent);
				return;
			} catch (AlreadyMintedException e) {
				log.warn("Order {} turned out to be minted already, not sending again", orderId);
				sentTransactions.markSubmitted(orderId);
				publishSubmitted(sentTransactions.findByOrderId(orderId).orElseThrow());
				return;
			} catch (MintAttemptException e) {
				int failures = sentTransactions.recordFailedAttempt(orderId, e.getMessage());
				if (failures >= mintProperties.maxAttempts()) {
					log.error("Mint of order {} failed after {} attempts: {}", orderId, failures, e.getMessage());
					sentTransactions.markFailed(orderId, e.getMessage());
					eventPublisher.publishMintFailed(orderId, e.getMessage(), null);
					return;
				}
				log.warn("Mint of order {} attempt {}/{} failed, retrying in {}: {}", orderId, failures,
						mintProperties.maxAttempts(), mintProperties.retryWait(), e.getMessage());
				sleep();
			}
		}
	}

	/**
	 * One attempt: simulate, sign, save the hash, send.
	 *
	 * @return the row with the nonce and hash of the accepted transaction
	 */
	private SentTransaction attempt(SentTransaction row, String callData) throws MintAttemptException {
		String contract = chainProperties.contractAddress();
		String minter = minterCredentials.getAddress();

		// Simulation: a revert here costs nothing, while a sent transaction that
		// reverts burns gas and needs the receipt check to be noticed.
		EthCall simulation = rpc(() -> web3j.ethCall(Transaction.createEthCallTransaction(minter, contract, callData),
				DefaultBlockParameterName.PENDING).send());
		if (simulation.hasError()) {
			String reason = RevertDecoder.describe(simulation.getError().getData());
			if (RevertDecoder.MINT_ORDER_ALREADY_SUBMITTED.equals(reason)) {
				throw new AlreadyMintedException();
			}
			throw new MintAttemptException("mint() would revert: " + reason);
		}

		BigInteger nonce = rpc(() -> web3j.ethGetTransactionCount(minter, DefaultBlockParameterName.PENDING).send())
				.getTransactionCount();
		ChainProperties.Gas gas = chainProperties.gas();
		RawTransaction transaction = RawTransaction.createTransaction(chainProperties.chainId(), nonce,
				gas.gasLimit(), contract, BigInteger.ZERO, callData, gas.maxPriorityFeePerGasWei(),
				gas.maxFeePerGasWei());
		String signed = transactionManager.sign(transaction);
		String txHash = Hash.sha3(signed);

		// Saved before sending: if chain-service dies right after the send, the
		// next attempt finds this hash and asks the node instead of minting again.
		sentTransactions.saveSigned(row.orderId(), nonce.longValueExact(), txHash);

		EthSendTransaction response = rpc(() -> web3j.ethSendRawTransaction(signed).send());
		if (response.hasError()) {
			throw new MintAttemptException("node rejected the transaction: " + response.getError().getMessage());
		}
		return sentTransactions.findByOrderId(row.orderId()).orElseThrow();
	}

	/**
	 * Whether an earlier attempt already got the mint through: the contract has
	 * the order id (mined), or the node knows the saved hash (still pending).
	 */
	private boolean alreadySent(SentTransaction row, byte[] onchainId) {
		boolean mined = rpc(() -> eurDemo.isMintOrderSubmitted(onchainId).send());
		if (mined) {
			log.warn("Order {} is already minted on-chain (earlier attempt), not sending again", row.orderId());
			return true;
		}
		if (row.txHash() != null && rpc(() -> web3j.ethGetTransactionByHash(row.txHash()).send())
				.getTransaction().isPresent()) {
			log.warn("Order {} has a pending transaction {} from an earlier attempt, not sending again",
					row.orderId(), row.txHash());
			return true;
		}
		return false;
	}

	/**
	 * MintSubmitted needs the hash and the nonce. If an earlier attempt got the
	 * mint through but its hash wasn't saved, there is nothing to say: the event
	 * reader publishes MintConfirmed, and core-api accepts it from APPROVED.
	 */
	private void publishSubmitted(SentTransaction row) {
		if (row.txHash() == null || row.nonce() == null) {
			log.warn("Order {} is minted but its transaction hash is unknown; MintConfirmed will close it",
					row.orderId());
			return;
		}
		eventPublisher.publishMintSubmitted(row.orderId(), row.txHash(), row.nonce());
	}

	private Optional<MintApprovedMessage> parse(String json) {
		try {
			MintApprovedMessage message = objectMapper.readValue(json, MintApprovedMessage.class);
			String problem = null;
			if (!Constants.MINT_APPROVED.equals(message.eventType())) {
				problem = "eventType is " + message.eventType();
			} else if (!Integer.valueOf(Constants.EVENT_VERSION).equals(message.version())) {
				problem = "unsupported version " + message.version();
			} else if (message.orderId() == null || message.payload() == null) {
				problem = "orderId or payload missing";
			} else if (message.payload().wallet() == null || !WalletUtils.isValidAddress(message.payload().wallet())) {
				problem = "invalid wallet " + message.payload().wallet();
			} else if (message.payload().amountCents() == null || message.payload().amountCents() <= 0) {
				problem = "invalid amountCents " + message.payload().amountCents();
			}
			if (problem != null) {
				log.error("Skipping invalid message on {}: {} ({})", Constants.MINT_ORDERS_TOPIC, problem, json);
				return Optional.empty();
			}
			return Optional.of(message);
		} catch (JacksonException e) {
			log.error("Skipping unparsable message on {}: {} ({})", Constants.MINT_ORDERS_TOPIC, e.getMessage(), json);
			return Optional.empty();
		}
	}

	@FunctionalInterface
	private interface RpcCall<T> {
		T call() throws Exception;
	}

	/** Runs an RPC call; a connection failure becomes RpcUnavailableException (infrastructure). */
	private <T> T rpc(RpcCall<T> call) {
		try {
			return call.call();
		} catch (IOException e) {
			throw new RpcUnavailableException("RPC node " + chainProperties.rpcUrl() + " is not reachable", e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("RPC call failed: " + e.getMessage(), e);
		}
	}

	private void sleep() {
		try {
			Thread.sleep(mintProperties.retryWait());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting to retry a mint", e);
		}
	}
}
