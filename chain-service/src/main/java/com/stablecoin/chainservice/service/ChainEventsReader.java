package com.stablecoin.chainservice.service;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.abi.EventEncoder;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import com.stablecoin.chainservice.config.ChainProperties;
import com.stablecoin.chainservice.config.ReaderProperties;
import com.stablecoin.chainservice.generated.contracts.EURDemo;
import com.stablecoin.chainservice.infra.clients.KafkaPublisher;
import com.stablecoin.chainservice.infra.clients.KafkaPublisher.KafkaPublishException;
import com.stablecoin.chainservice.infra.repository.PostgresBlockCheckpointsRepository;
import com.stablecoin.chainservice.infra.repository.PostgresObservedEventsRepository;
import com.stablecoin.chainservice.infra.repository.PostgresObservedEventsRepository.PendingEvent;
import com.stablecoin.chainservice.infra.repository.PostgresSentTransactionsRepository;
import com.stablecoin.chainservice.models.EventEnvelope;
import com.stablecoin.chainservice.models.SentTransaction;
import com.stablecoin.chainservice.util.Constants;
import com.stablecoin.chainservice.util.Conversions;
import com.stablecoin.chainservice.util.EventIds;

/**
 * Reads TokensEmitted and TokensRedeemed from the contract and publishes
 * MintConfirmed / RedeemConfirmed (RF-CHAIN-07..09). Run by ChainEventsReaderJob.
 *
 * Each run has three steps:
 * 1. {@link #readNextRange()}: logs from the block after the checkpoint up to the
 *    last block with N confirmations. The rows for observed_events and the new
 *    checkpoint are saved in ONE transaction, so a crash either keeps both or
 *    neither, and the range is read again.
 * 2. {@link #publishPending()}: sends the unpublished rows and marks each one
 *    after Kafka's ack. observed_events is chain-service's outbox for these events.
 * 3. {@link #checkSubmittedReceipts()}: a mint that was accepted but reverted
 *    in its block emits no TokensEmitted, so it would wait forever. Its receipt
 *    says so, and MintFailed is published.
 */
@Service
public class ChainEventsReader {

	private static final Logger log = LoggerFactory.getLogger(ChainEventsReader.class);

	private static final String TOKENS_EMITTED_TOPIC = EventEncoder.encode(EURDemo.TOKENSEMITTED_EVENT);
	private static final String TOKENS_REDEEMED_TOPIC = EventEncoder.encode(EURDemo.TOKENSREDEEMED_EVENT);
	private static final int PUBLISH_BATCH = 100;

	/** A decoded log, ready for observed_events. */
	private record ObservedRow(String txHash, long logIndex, long blockNumber, String blockHash, String eventName,
			UUID eventId, UUID orderId, String onchainId, String payloadJson) {
	}

	private final Web3j web3j;
	private final ChainProperties chainProperties;
	private final ReaderProperties readerProperties;
	private final PostgresBlockCheckpointsRepository checkpoints;
	private final PostgresObservedEventsRepository observedEvents;
	private final PostgresSentTransactionsRepository sentTransactions;
	private final ChainEventPublisher eventPublisher;
	private final KafkaPublisher kafkaPublisher;
	private final TransactionTemplate transactionTemplate;

	public ChainEventsReader(Web3j web3j, ChainProperties chainProperties, ReaderProperties readerProperties,
			PostgresBlockCheckpointsRepository checkpoints, PostgresObservedEventsRepository observedEvents,
			PostgresSentTransactionsRepository sentTransactions, ChainEventPublisher eventPublisher,
			KafkaPublisher kafkaPublisher, TransactionTemplate transactionTemplate) {
		this.web3j = web3j;
		this.chainProperties = chainProperties;
		this.readerProperties = readerProperties;
		this.checkpoints = checkpoints;
		this.observedEvents = observedEvents;
		this.sentTransactions = sentTransactions;
		this.eventPublisher = eventPublisher;
		this.kafkaPublisher = kafkaPublisher;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * Reads one range of at most app.reader.maxBlockRange blocks.
	 *
	 * A log in block b has {@code head − b + 1} confirmations, so with N required
	 * the last block that can be read is {@code head − N + 1} (with N = 1, the
	 * head itself).
	 *
	 * @return true when the reader has caught up with the last confirmed block
	 */
	public boolean readNextRange() {
		long chainId = chainProperties.chainId();
		String contract = chainProperties.contractAddress();
		int confirmations = Math.max(1, chainProperties.confirmations());

		long head = rpc(() -> web3j.ethBlockNumber().send()).getBlockNumber().longValueExact();
		long lastConfirmedBlock = head - confirmations + 1;
		long from = checkpoints.findLastProcessedBlock(chainId, contract)
				.map(last -> last + 1)
				.orElse(readerProperties.startBlock());
		if (from > lastConfirmedBlock) {
			return true;
		}
		long to = Math.min(lastConfirmedBlock, from + readerProperties.maxBlockRange() - 1);

		EthFilter filter = new EthFilter(DefaultBlockParameter.valueOf(BigInteger.valueOf(from)),
				DefaultBlockParameter.valueOf(BigInteger.valueOf(to)), contract);
		filter.addOptionalTopics(TOKENS_EMITTED_TOPIC, TOKENS_REDEEMED_TOPIC);
		EthLog response = rpc(() -> web3j.ethGetLogs(filter).send());
		if (response.hasError()) {
			throw new RpcUnavailableException("eth_getLogs " + from + ".." + to + " failed: "
					+ response.getError().getMessage(), null);
		}

		List<ObservedRow> rows = new ArrayList<>();
		Map<Long, String> blockTimes = new HashMap<>();
		for (EthLog.LogResult<?> result : response.getLogs()) {
			Log logEntry = (Log) result.get();
			if (Boolean.TRUE.equals(logEntry.isRemoved())) {
				continue; // dropped by a reorg (RF-CHAIN-12 is out of V1; see the plan)
			}
			rows.add(decode(logEntry, head, chainId, blockTimes));
		}

		transactionTemplate.executeWithoutResult(status -> {
			for (ObservedRow row : rows) {
				observedEvents.insertIfAbsent(chainId, row.txHash(), row.logIndex(), row.blockNumber(),
						row.blockHash(), row.eventName(), row.eventId(), row.orderId(), row.payloadJson());
				if (Constants.TOKENS_EMITTED.equals(row.eventName())) {
					sentTransactions.markConfirmed(row.onchainId(), row.txHash());
				}
			}
			checkpoints.saveLastProcessedBlock(chainId, contract, to);
		});

		if (!rows.isEmpty()) {
			log.info("Read blocks {}..{} (head {}): {} contract events", from, to, head, rows.size());
		}
		return to >= lastConfirmedBlock;
	}

	/**
	 * Publishes the unpublished observed_events, in chain order. The first
	 * failure stops, so the events of one order don't go out of order; the rest
	 * wait for the next run.
	 */
	public void publishPending() {
		List<PendingEvent> pending;
		do {
			pending = observedEvents.findUnpublished(PUBLISH_BATCH);
			for (PendingEvent event : pending) {
				try {
					kafkaPublisher.sendAndWait(Constants.CHAIN_EVENTS_TOPIC, event.key(), event.payload());
				} catch (KafkaPublishException e) {
					log.warn("Could not publish {} {}, will retry on the next run: {}", event.eventName(),
							event.eventId(), e.getMessage());
					return;
				}
				observedEvents.markPublished(event.eventId());
				log.info("Published {} {} (key {})", eventType(event.eventName()), event.eventId(), event.key());
			}
		} while (pending.size() == PUBLISH_BATCH);
	}

	/** SUBMITTED mints whose receipt shows a revert become FAILED, with MintFailed. */
	public void checkSubmittedReceipts() {
		for (SentTransaction row : sentTransactions.findSubmitted()) {
			Optional<TransactionReceipt> receipt = rpc(
					() -> web3j.ethGetTransactionReceipt(row.txHash()).send()).getTransactionReceipt();
			if (receipt.isPresent() && !receipt.get().isStatusOK()) {
				String reason = "transaction reverted on-chain in block " + receipt.get().getBlockNumber();
				log.error("Mint of order {} failed: {} ({})", row.orderId(), reason, row.txHash());
				sentTransactions.markFailed(row.orderId(), reason);
				eventPublisher.publishMintFailed(row.orderId(), reason, row.txHash());
			}
			// No receipt yet: still pending. A transaction the node drops for good
			// stays SUBMITTED (no resend in V1; see the plan's known limitations).
		}
	}

	private ObservedRow decode(Log logEntry, long head, long chainId, Map<Long, String> blockTimes) {
		String topic0 = logEntry.getTopics().get(0);
		boolean emitted = TOKENS_EMITTED_TOPIC.equals(topic0);
		String txHash = logEntry.getTransactionHash().toLowerCase(Locale.ROOT);
		long logIndex = logEntry.getLogIndex().longValueExact();
		long blockNumber = logEntry.getBlockNumber().longValueExact();

		String account;
		BigInteger amountUnits;
		byte[] orderIdBytes;
		if (emitted) {
			EURDemo.TokensEmittedEventResponse event = EURDemo.getTokensEmittedEventFromLog(logEntry);
			account = event.to;
			amountUnits = event.amount;
			orderIdBytes = event.orderId;
		} else {
			EURDemo.TokensRedeemedEventResponse event = EURDemo.getTokensRedeemedEventFromLog(logEntry);
			account = event.account;
			amountUnits = event.amount;
			orderIdBytes = event.orderId;
		}
		UUID orderId = Conversions.bytes32ToOrderId(orderIdBytes).orElse(null);
		String onchainId = Conversions.toHex(orderIdBytes);
		if (orderId == null) {
			log.warn("{} in tx {} has a bytes32 {} that is not a core-api order id; published with orderId null",
					emitted ? Constants.TOKENS_EMITTED : Constants.TOKENS_REDEEMED, txHash, onchainId);
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(emitted ? "wallet" : "holder", Keys.toChecksumAddress(account));
		payload.put("amountCents", Conversions.unitsToCents(amountUnits).orElse(null));
		payload.put("amountUnits", amountUnits.toString());
		payload.put("onchainId", onchainId);
		payload.put("txHash", txHash);
		payload.put("blockNumber", blockNumber);
		payload.put("confirmations", head - blockNumber + 1);

		String eventType = emitted ? Constants.MINT_CONFIRMED : Constants.REDEEM_CONFIRMED;
		UUID eventId = EventIds.forLog(chainId, txHash, logIndex);
		String occurredAt = blockTimes.computeIfAbsent(blockNumber, this::blockTimestamp);
		String json = eventPublisher.toJson(
				new EventEnvelope(eventId, eventType, Constants.EVENT_VERSION, occurredAt, orderId, payload));

		return new ObservedRow(txHash, logIndex, blockNumber, logEntry.getBlockHash().toLowerCase(Locale.ROOT),
				emitted ? Constants.TOKENS_EMITTED : Constants.TOKENS_REDEEMED, eventId, orderId, onchainId, json);
	}

	/** When the fact happened: the timestamp of the block that holds the log. */
	private String blockTimestamp(long blockNumber) {
		BigInteger seconds = rpc(() -> web3j.ethGetBlockByNumber(
				DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), false).send())
				.getBlock().getTimestamp();
		return Instant.ofEpochSecond(seconds.longValueExact()).toString();
	}

	private static String eventType(String eventName) {
		return Constants.TOKENS_EMITTED.equals(eventName) ? Constants.MINT_CONFIRMED : Constants.REDEEM_CONFIRMED;
	}

	@FunctionalInterface
	private interface RpcCall<T> {
		T call() throws IOException;
	}

	private <T> T rpc(RpcCall<T> call) {
		try {
			return call.call();
		} catch (IOException e) {
			throw new RpcUnavailableException("RPC node " + chainProperties.rpcUrl() + " is not reachable", e);
		}
	}
}
