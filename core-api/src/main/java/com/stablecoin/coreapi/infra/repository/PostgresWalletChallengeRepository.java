package com.stablecoin.coreapi.infra.repository;

import com.stablecoin.coreapi.util.Constants;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresWalletChallengeRepository {

    private final JdbcClient jdbc;

    public PostgresWalletChallengeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the wallet as PENDING if it does not exist yet (challenges reference wallets). */
    public void insertPendingWalletIfAbsent(String walletAddress) {
        jdbc.sql(Constants.UPSERT_PENDING_WALLET_QUERY)
                .param("walletAddress", walletAddress)
                .update();
    }

    public void insertIntoWalletChallenges(String nonce, String walletAddress, String message,
            OffsetDateTime expiresAt) {
        jdbc.sql(Constants.INSERT_WALLET_CHALLENGE_QUERY)
                .param("nonce", nonce)
                .param("walletAddress", walletAddress)
                .param("message", message)
                .param("expiresAt", expiresAt)
                .update();
    }
}
