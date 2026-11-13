package com.stablecoin.coreapi.infra.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.stablecoin.coreapi.generated.model.Wallet;
import com.stablecoin.coreapi.generated.model.WalletStatus;
import com.stablecoin.coreapi.models.WalletChallengeData;
import com.stablecoin.coreapi.util.Constants;

@Repository
public class PostgresWalletsRepository {

    private final JdbcClient jdbc;

    public PostgresWalletsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Creates the wallet as PENDING if it does not exist yet (challenges reference
     * wallets).
     */
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

    /** @return true if the challenge was still unused and is now consumed */
    public boolean markWalletChallengeUsed(String nonce, String walletAddress) {
        return jdbc.sql(Constants.MARK_WALLET_CHALLENGE_USED_QUERY)
                .param("nonce", nonce)
                .param("walletAddress", walletAddress)
                .update() == 1;
    }

    /** @return the wallet's verified_at after the update */
    public OffsetDateTime markWalletVerified(String walletAddress) {
        return jdbc.sql(Constants.MARK_WALLET_VERIFIED_QUERY)
                .param("walletAddress", walletAddress)
                .query((rs, rowNum) -> rs.getObject("verified_at", OffsetDateTime.class))
                .single();
    }

    /**
     * Returns the challenge even if it is used or expired; the caller decides which
     * error applies. Uses a RowMapper with {@code optional()}: since Spring 7,
     * {@code query(ResultSetExtractor)} throws if the extractor returns null.
     */
    public Optional<WalletChallengeData> getWalletChallengeByAddressAndNonce(String walletAddress, String nonce) {
        return jdbc.sql(Constants.SELECT_WALLET_CHALLENGE_BY_NONCE_QUERY)
                .param("nonce", nonce)
                .param("walletAddress", walletAddress)
                .query((rs, rowNum) -> {
                    WalletChallengeData challengeData = new WalletChallengeData();
                    challengeData.setNonce(rs.getString("nonce"));
                    challengeData.setWalletAddress(rs.getString("wallet_address"));
                    challengeData.setMessage(rs.getString("message"));
                    challengeData.setExpiresAt(rs.getObject("expires_at", OffsetDateTime.class));
                    challengeData.setUsedAt(rs.getObject("used_at", OffsetDateTime.class));
                    challengeData.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                    return challengeData;
                })
                .optional();
    }

    /**
     * Get the wallet by its address. Returns an Optional<Wallet> which is empty if
     * the wallet does not exist.
     */
    public Optional<Wallet> getWalletByAddress(String walletAddress) {
        return jdbc.sql(Constants.SELECT_WALLET_BY_ADDRESS_QUERY)
                .param("walletAddress", walletAddress)
                .query((rs, rowNum) -> {
                    Wallet wallet = new Wallet();
                    WalletStatus walletStatus = WalletStatus.fromValue(rs.getString("status"));
                    wallet.setWalletAddress(rs.getString("wallet_address"));
                    wallet.setStatus(walletStatus);
                    wallet.setVerifiedAt(rs.getObject("verified_at", OffsetDateTime.class));
                    return wallet;
                })
                .optional();
    }
}
