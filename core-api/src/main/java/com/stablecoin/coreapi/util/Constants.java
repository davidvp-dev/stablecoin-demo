package com.stablecoin.coreapi.util;

public final class Constants {

	public static final String SIWE_MESSAGE_TEMPLATE = """
			{domain} wants you to sign in with your Ethereum account:
			{address}

			Verify wallet ownership for {appName}.

			URI: {uri}
			Version: 1
			Chain ID: {chainId}
			Nonce: {nonce}
			Issued At: {issuedAt}
			Expiration Time: {expiresAt}""";

	private Constants() {
	}
}
