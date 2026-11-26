package com.stablecoin.chainservice.config;

import java.math.BigInteger;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to the chain and the EURDemo contract ({@code app.chain.*} in
 * application.properties). The values for each network come from .env.
 *
 * @param rpcUrl            JSON-RPC endpoint of the node
 * @param chainId           expected chain id; also used to sign transactions
 *                          (EIP-155), so a transaction for one network can't be
 *                          replayed on another
 * @param contractAddress   EURDemo address, see docs/deployments.md
 * @param minterPrivateKey  key of the account holding MINTER_ROLE
 * @param confirmations     blocks a contract event must be buried under before
 *                          it is published (1 on Anvil, 2-3 on Sepolia)
 * @param gas               fee caps and gas limit for the transactions sent
 */
@ConfigurationProperties(prefix = "app.chain")
public record ChainProperties(
		String rpcUrl,
		long chainId,
		String contractAddress,
		String minterPrivateKey,
		int confirmations,
		Gas gas) {

	/**
	 * EIP-1559 fees. maxFeePerGas is a cap, not a price: the transaction pays the
	 * block's base fee plus the priority fee, up to the cap.
	 */
	public record Gas(BigInteger maxFeePerGasWei, BigInteger maxPriorityFeePerGasWei, BigInteger gasLimit) {
	}
}
