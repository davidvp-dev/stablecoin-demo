package com.stablecoin.chainservice.util;

/** Topics and event names of docs/events.md. */
public final class Constants {

	public static final String MINT_ORDERS_TOPIC = "mint-orders.v1";
	public static final String CHAIN_EVENTS_TOPIC = "chain-events.v1";

	public static final String MINT_APPROVED = "MintApproved";
	public static final String MINT_SUBMITTED = "MintSubmitted";
	public static final String MINT_CONFIRMED = "MintConfirmed";
	public static final String MINT_FAILED = "MintFailed";
	public static final String REDEEM_CONFIRMED = "RedeemConfirmed";

	/** Contract event names, as stored in observed_events.event_name. */
	public static final String TOKENS_EMITTED = "TokensEmitted";
	public static final String TOKENS_REDEEMED = "TokensRedeemed";

	/** Version of the event contract in docs/events.md. */
	public static final int EVENT_VERSION = 1;

	private Constants() {
	}
}
