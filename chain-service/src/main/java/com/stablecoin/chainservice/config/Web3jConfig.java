package com.stablecoin.chainservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticEIP1559GasProvider;
import org.web3j.tx.response.NoOpProcessor;

import com.stablecoin.chainservice.generated.contracts.EURDemo;

/**
 * Binds the generated EURDemo wrapper (target/generated-sources/web3j, built
 * from
 * contracts/abi/EURDemo.abi.json) to the node and the minter account.
 */
@Configuration
@EnableConfigurationProperties(ChainProperties.class)
public class Web3jConfig {

	@Bean(destroyMethod = "shutdown")
	public Web3j web3j(ChainProperties properties) {
		return Web3j.build(new HttpService(properties.rpcUrl()));
	}

	/**
	 * The minter account. Its address is checked against MINTER_ROLE by GET /chain/health.
	 */
	@Bean
	public Credentials minterCredentials(ChainProperties properties) {
		return Credentials.create(properties.minterPrivateKey());
	}

	/**
	 * Signs transactions locally with the minter key and the chain id, and sends
	 * them as raw transactions. The nonce is asked from the node on each send
	 * (pending count), which is enough while chain-service sends one transaction
	 * at a time (IMPLEMENTATION_PLAN_V1, "Nonce management").
	 *
	 * NoOpProcessor: a send returns as soon as the node accepts the transaction,
	 * with only its hash, instead of polling for the receipt. chain-service
	 * publishes MintSubmitted at that point, and learns about the confirmation
	 * from the TokensEmitted log after N blocks (Day 3).
	 *
	 * Not named "transactionManager": that name belongs to the JPA transaction
	 * manager, which has nothing to do with blockchain transactions.
	 */
	@Bean
	public TransactionManager minterTransactionManager(Web3j web3j, Credentials minterCredentials,
			ChainProperties properties) {
		return new RawTransactionManager(web3j, minterCredentials, properties.chainId(), new NoOpProcessor(web3j));
	}

	@Bean
	public ContractGasProvider contractGasProvider(ChainProperties properties) {
		ChainProperties.Gas gas = properties.gas();
		return new StaticEIP1559GasProvider(properties.chainId(), gas.maxFeePerGasWei(),
				gas.maxPriorityFeePerGasWei(), gas.gasLimit());
	}

	/**
	 * The contract, ready to call. Views (balanceOf, totalSupply, hasRole...) are
	 * eth_call and cost nothing; mint() is signed by the minter and sent.
	 */
	@Bean
	public EURDemo eurDemo(ChainProperties properties, Web3j web3j, TransactionManager minterTransactionManager,
			ContractGasProvider contractGasProvider) {
		return EURDemo.load(properties.contractAddress(), web3j, minterTransactionManager, contractGasProvider);
	}
}
