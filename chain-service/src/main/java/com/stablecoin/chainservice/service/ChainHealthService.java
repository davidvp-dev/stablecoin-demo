package com.stablecoin.chainservice.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;

import com.stablecoin.chainservice.config.ChainProperties;
import com.stablecoin.chainservice.generated.contracts.EURDemo;
import com.stablecoin.chainservice.generated.model.ChainHealth;
import com.stablecoin.chainservice.generated.model.HealthChecks;
import com.stablecoin.chainservice.generated.model.HealthStatus;
import com.stablecoin.chainservice.infra.repository.PostgresBlockCheckpointsRepository;

/**
 * Builds GET /chain/health: whether chain-service is configured for the chain it
 * is connected to, and how far the event reader is behind the node.
 *
 * Every check runs on each call, so the answer reflects the chain right now:
 * after an Anvil restart the contract is gone and the next call reports it,
 * without restarting chain-service.
 */
@Service
public class ChainHealthService {

	/** EURD has 6 decimals: 1 cent = 10,000 units (docs/events.md). */
	static final BigInteger EXPECTED_DECIMALS = BigInteger.valueOf(6);

	private final Web3j web3j;
	private final EURDemo eurDemo;
	private final Credentials minterCredentials;
	private final ChainProperties properties;
	private final PostgresBlockCheckpointsRepository checkpointsRepository;

	public ChainHealthService(Web3j web3j, EURDemo eurDemo, Credentials minterCredentials,
			ChainProperties properties, PostgresBlockCheckpointsRepository checkpointsRepository) {
		this.web3j = web3j;
		this.eurDemo = eurDemo;
		this.minterCredentials = minterCredentials;
		this.properties = properties;
		this.checkpointsRepository = checkpointsRepository;
	}

	/**
	 * @throws RpcUnavailableException if the node can't be reached; the checks
	 *                                 need it, so there is no partial answer
	 */
	public ChainHealth getHealth() {
		List<String> problems = new ArrayList<>();
		String contractAddress = properties.contractAddress();
		String minterAddress = minterCredentials.getAddress();

		long nodeChainId = rpc(web3j.ethChainId()).getChainId().longValueExact();
		long headBlock = rpc(web3j.ethBlockNumber()).getBlockNumber().longValueExact();

		boolean chainIdMatches = nodeChainId == properties.chainId();
		if (!chainIdMatches) {
			problems.add("The node is chain " + nodeChainId + ", but chain-service is configured for chain "
					+ properties.chainId() + " (app.chain.chainId)");
		}

		String code = rpc(web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST)).getCode();
		boolean contractDeployed = code != null && !code.equals("0x");

		// The contract calls only make sense if there is a contract to call.
		boolean decimalsMatch = false;
		boolean minterHasRole = false;
		Boolean paused = null;
		if (contractDeployed) {
			BigInteger decimals = call(() -> eurDemo.decimals().send());
			decimalsMatch = EXPECTED_DECIMALS.equals(decimals);
			if (!decimalsMatch) {
				problems.add("The contract at " + contractAddress + " has " + decimals + " decimals, expected "
						+ EXPECTED_DECIMALS + ". Is it EURDemo?");
			}
			minterHasRole = call(() -> eurDemo.hasRole(eurDemo.MINTER_ROLE().send(), minterAddress).send());
			if (!minterHasRole) {
				problems.add("The minter account " + Keys.toChecksumAddress(minterAddress)
						+ " does not hold MINTER_ROLE. Check MINTER_PRIVATE_KEY_* in .env");
			}
			paused = call(() -> eurDemo.paused().send());
		} else {
			problems.add("There is no contract at " + contractAddress + " on chain " + nodeChainId
					+ ". Deploy it (docs/deployments.md) or fix EURD_CONTRACT_ADDRESS_* in .env");
		}

		Optional<Long> lastProcessedBlock = checkpointsRepository.findLastProcessedBlock(properties.chainId(),
				contractAddress);

		HealthChecks checks = new HealthChecks();
		checks.setChainIdMatches(chainIdMatches);
		checks.setContractDeployed(contractDeployed);
		checks.setDecimalsMatch(decimalsMatch);
		checks.setMinterHasRole(minterHasRole);

		ChainHealth health = new ChainHealth();
		health.setStatus(problems.isEmpty() ? HealthStatus.UP : HealthStatus.DOWN);
		health.setChainId(nodeChainId);
		health.setExpectedChainId(properties.chainId());
		health.setContractAddress(Keys.toChecksumAddress(contractAddress));
		health.setMinterAddress(Keys.toChecksumAddress(minterAddress));
		health.setPaused(paused);
		health.setChecks(checks);
		health.setProblems(problems);
		health.setHeadBlock(headBlock);
		health.setLastProcessedBlock(lastProcessedBlock.orElse(null));
		health.setLagBlocks(lastProcessedBlock.map(last -> headBlock - last).orElse(null));
		health.setConfirmationsRequired(properties.confirmations());
		return health;
	}

	/** Sends a JSON-RPC request; a connection failure or an RPC error means the node is unavailable. */
	private <T extends Response<?>> T rpc(Request<?, T> request) {
		try {
			T response = request.send();
			if (response.hasError()) {
				throw new RpcUnavailableException(
						"RPC " + request.getMethod() + " failed: " + response.getError().getMessage(), null);
			}
			return response;
		} catch (IOException e) {
			throw new RpcUnavailableException("RPC node " + properties.rpcUrl() + " is not reachable", e);
		}
	}

	@FunctionalInterface
	private interface ContractCall<T> {
		T send() throws Exception;
	}

	/**
	 * A contract view call (eth_call). Losing the connection midway is the same
	 * as not reaching the node; any other error (a revert, an undecodable
	 * answer) is not expected from EURDemo's views and is rethrown as is.
	 */
	private <T> T call(ContractCall<T> call) {
		try {
			return call.send();
		} catch (IOException e) {
			throw new RpcUnavailableException("RPC node " + properties.rpcUrl() + " is not reachable", e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Contract call failed: " + e.getMessage(), e);
		}
	}
}
