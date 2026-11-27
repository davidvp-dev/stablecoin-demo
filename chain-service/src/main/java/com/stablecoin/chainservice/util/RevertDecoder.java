package com.stablecoin.chainservice.util;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.web3j.crypto.Hash;

/**
 * Turns the revert data of an eth_call into the name of the custom error, so
 * MintFailed carries a reason a person can read ("EnforcedPause()") instead of
 * "0xd93c0665".
 *
 * The list is the errors in contracts/abi/EURDemo.abi.json. If the contract gets
 * a new error, add it here; an unknown one is still reported with its selector.
 */
public final class RevertDecoder {

	/** Revert data starting with this selector means the order was already minted. */
	public static final String MINT_ORDER_ALREADY_SUBMITTED = "MintOrderAlreadySubmitted(bytes32)";

	private static final Map<String, String> ERRORS_BY_SELECTOR = Stream.of(
			"AccessControlBadConfirmation()",
			"AccessControlUnauthorizedAccount(address,bytes32)",
			"ERC20InsufficientAllowance(address,uint256,uint256)",
			"ERC20InsufficientBalance(address,uint256,uint256)",
			"ERC20InvalidApprover(address)",
			"ERC20InvalidReceiver(address)",
			"ERC20InvalidSender(address)",
			"ERC20InvalidSpender(address)",
			"EnforcedPause()",
			"ExpectedPause()",
			MINT_ORDER_ALREADY_SUBMITTED,
			"RedeemOrderAlreadySubmitted(bytes32)",
			"UserBlacklisted(address)",
			"ZeroAddress()",
			"ZeroAmount()")
			.collect(Collectors.toMap(RevertDecoder::selector, Function.identity()));

	private RevertDecoder() {
	}

	/** First 4 bytes of keccak256 of the signature, as 0x hex. */
	static String selector(String signature) {
		return Hash.sha3String(signature).substring(0, 10);
	}

	/**
	 * @param revertData 0x hex returned by the node (may be null or empty)
	 * @return the error signature, or a description with the raw selector
	 */
	public static String describe(String revertData) {
		if (revertData == null || revertData.length() < 10) {
			return "execution reverted without data";
		}
		String selector = revertData.substring(0, 10).toLowerCase();
		return ERRORS_BY_SELECTOR.getOrDefault(selector, "unknown error " + selector);
	}
}
