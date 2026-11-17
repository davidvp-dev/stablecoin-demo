package com.stablecoin.coreapi.infra.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.stablecoin.coreapi.generated.model.BankTransfer;
import com.stablecoin.coreapi.generated.model.TransferDirection;
import com.stablecoin.coreapi.util.Constants;

@Repository
public class PostgresBankTransfersRepository {

    private final JdbcClient jdbc;

    public PostgresBankTransfersRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records a simulated bank transfer and returns it as the API model.
     *
     * @param outcome     MATCHED, REJECTED, UNASSIGNED, REFUND or PAYOUT. The
     *                    API's TransferOutcome only has the first three, because
     *                    REFUND and PAYOUT are outgoing transfers the API never
     *                    returns, so it is passed as a string.
     * @param mintOrderId the order the transfer belongs to, or null when
     *                    UNASSIGNED (the table checks both go together)
     */
    public BankTransfer insertBankTransfer(TransferDirection direction, String reference, long amountCents,
            String counterpartyIban, String outcome, UUID mintOrderId) {
        return jdbc.sql(Constants.INSERT_BANK_TRANSFER_QUERY)
                .param("direction", direction.getValue())
                .param("reference", reference)
                .param("amountCents", amountCents)
                .param("counterpartyIban", counterpartyIban)
                .param("outcome", outcome)
                .param("mintOrderId", mintOrderId)
                .query((rs, rowNum) -> {
                    BankTransfer transfer = new BankTransfer();
                    transfer.setId(rs.getObject("id", UUID.class));
                    transfer.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                    transfer.setDirection(direction);
                    transfer.setReference(reference);
                    transfer.setAmountCents(amountCents);
                    transfer.setOrderId(mintOrderId);
                    return transfer;
                })
                .single();
    }
}
