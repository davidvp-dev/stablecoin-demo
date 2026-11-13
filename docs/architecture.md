# Architecture

This document is the English, versioned copy of the architecture in the functional requirements. The integration contracts live next to it:

- REST: [`core-api/src/main/resources/api-definition.yml`](../core-api/src/main/resources/api-definition.yml) and [`chain-service/src/main/resources/api-definition.yml`](../chain-service/src/main/resources/api-definition.yml)
- Kafka: [`events.md`](events.md)

## Components

```mermaid
flowchart LR
    client([Client]) --> portal
    admin([Technical admin]) -- Foundry scripts --> contract
    admin --> portal

    subgraph backend [Backend]
        coreapi[core-api]
        chain[chain-service]
    end

    portal -- "REST /api + SSE" --> coreapi
    portal -- "wallet: redeem(), balanceOf()" --> contract[(EURD contract)]
    coreapi -- "mint-orders.v1" --> kafka{{Kafka}}
    kafka -- "mint-orders.v1" --> chain
    chain -- "chain-events.v1" --> kafka
    kafka -- "chain-events.v1" --> coreapi
    coreapi -- "REST /chain/supply" --> chain
    chain -- "JSON-RPC: mint(), events, totalSupply()" --> node[RPC node: Anvil / Sepolia]
    node --- contract
    coreapi --- coredb[(core_db)]
    chain --- chaindb[(chain_db)]
```

| Component | Responsibility | Exposes | Consumes |
|---|---|---|---|
| EURD contract | Token state: balances, supply, roles, pause and blacklist | On-chain functions and events | — |
| core-api | Wallets, orders, simulated bank, ledger and reconciliation | REST `/api`, SSE, topic `mint-orders.v1` | Topic `chain-events.v1`, chain-service REST |
| chain-service | The only backend component that writes and reads the chain | REST `/chain`, topic `chain-events.v1` | Topic `mint-orders.v1`, JSON-RPC |
| portal | Client UI and reconciliation panel | Web screens | REST `/api`, SSE, the user's wallet |
| Kafka | Asynchronous transport between core-api and chain-service | Topics | — |
| PostgreSQL | Persistence, one database per service (`core_db`, `chain_db`) | SQL | — |

No component reads another component's database. Redeem is the only operation that reaches the contract without going through the backend: the client's wallet signs and sends it.

## Conventions

- **Amounts:** the backend always works in integer euro cents. The contract has 6 decimals, so 1 cent = 10,000 token units. The conversion only happens in chain-service and the portal.
- **Addresses:** normalized before being stored or compared (stored lowercase, returned in EIP-55 checksum format).
- **Order ids:** UUIDs in the backend, bytes32 on-chain (rule in [`events.md`](events.md#order-id-uuid--bytes32)).

## Transaction boundaries

core-api uses `JdbcClient` with Spring's declarative transactions. The rule is:

> A service method gets `@Transactional` when it makes **more than one write that must succeed or fail together**. A single statement does not need it: with autocommit, one statement is already atomic.

Under `@Transactional`, Spring opens one transaction when the method starts, every `JdbcClient` call inside it uses that transaction's connection, and it commits when the method returns. Any `RuntimeException` (including `ApiException`) rolls back all the writes. Without the annotation, each call autocommits on its own, so a failure halfway through leaves the earlier writes committed.

### CU-01: generate a challenge (`WalletsService.generateWalletChallenge`)

| Step | Statement |
|---|---|
| 1 | `INSERT INTO wallets ... ON CONFLICT DO NOTHING` registers the wallet as `PENDING` |
| 2 | `INSERT INTO wallet_challenges ...` stores the nonce, message and expiry |

The order is forced by the schema: `wallet_challenges.wallet_address` is a foreign key to `wallets`. Here the transaction is mostly for consistency (one request = one unit of work). If step 2 failed after step 1 committed, the result would be a `PENDING` wallet with no challenge. That is still a valid state, and the next request reuses it because step 1 is idempotent. Concurrent requests for the same address are safe without the transaction too, because of `ON CONFLICT`.

### CU-01: verify the signature (`WalletsService.verifyWalletSignature`)

| Step | Statement |
|---|---|
| 1 | `SELECT` the challenge by nonce and address. The service then returns a specific error: `INVALID_NONCE` (not found), `NONCE_ALREADY_USED` or `CHALLENGE_EXPIRED` |
| — | Recover the EIP-191 signer in Java and compare it with the wallet address |
| 2 | `UPDATE wallet_challenges SET used_at = NOW() WHERE ... AND used_at IS NULL` consumes the nonce |
| 3 | `UPDATE wallets SET status = 'VERIFIED', verified_at = COALESCE(verified_at, NOW()) ... RETURNING verified_at` |

Here the transaction is essential. Without it:

- If step 2 committed and step 3 failed, the nonce would be burned while the wallet stayed `PENDING`. The user would have to request and sign a new challenge.
- Step 3 must never be committed without step 2, or the same signature could verify the wallet again later.

**Single use under concurrency.** The `SELECT` in step 1 alone does not stop two concurrent requests that send the same signature: both can read the challenge as unused. Step 2 handles that with a conditional `UPDATE`. The `used_at IS NULL` guard is checked when the row is updated, and PostgreSQL's row lock makes the second request wait for the first one to finish. It then re-checks the condition, finds `used_at` already set and updates 0 rows. The service treats 0 rows as `NONCE_ALREADY_USED` and throws, which rolls back that request's transaction. This gives single use without `SELECT ... FOR UPDATE` or a stricter isolation level than PostgreSQL's default `READ COMMITTED`.

`COALESCE(verified_at, NOW())` keeps the first verification timestamp if an already verified wallet signs a new challenge. Together with the `CHECK ((status = 'VERIFIED') = (verified_at IS NOT NULL))` constraint, this keeps `status` and `verified_at` consistent.

### Pitfall: self-invocation

`@Transactional` works through a Spring proxy, so it only applies when the method is called from another bean (for example, the controller calling `WalletsService`). If one method of `WalletsService` calls another `@Transactional` method of the same class, the call skips the proxy and the inner annotation is ignored. The transaction boundary is therefore placed on the public service method that the controller calls.

## Order statuses

The code uses English names. Mapping to the names in the functional requirements:

| Requirements (ES) | Code | Mint | Redeem |
|---|---|:-:|:-:|
| CREADA | `CREATED` | ✓ | ✓ |
| PAGADA | `PAID` | ✓ | |
| RECHAZADA | `REJECTED` | ✓ | |
| CADUCADA | `EXPIRED` | ✓ | ✓ |
| APROBADA | `APPROVED` | ✓ | |
| ENVIADA | `SUBMITTED` | ✓ | |
| COMPLETADA | `COMPLETED` | ✓ | ✓ |
| FALLIDA | `FAILED` | ✓ | |
| TOKENS_QUEMADOS | `TOKENS_BURNED` | | ✓ |
| REVISION | `UNDER_REVIEW` | | ✓ |
| PENDIENTE / VERIFICADA (wallet) | `PENDING` / `VERIFIED` | | |
| ENTRANTE / SALIENTE (transfer) | `INCOMING` / `OUTGOING` | | |
| CUADRA / DESCUADRE (reconciliation) | `BALANCED` / `MISMATCH` | | |

### Mint order

Any transition not in this diagram is rejected and recorded (RF-CORE-22).

```mermaid
stateDiagram-v2
    [*] --> CREATED: client creates the order
    CREATED --> PAID: transfer with the right amount
    CREATED --> REJECTED: different amount, refund
    CREATED --> EXPIRED: expires without payment
    PAID --> APPROVED: automatic approval
    APPROVED --> SUBMITTED: MintSubmitted
    APPROVED --> FAILED: MintFailed
    SUBMITTED --> COMPLETED: MintConfirmed
    SUBMITTED --> FAILED: MintFailed
    COMPLETED --> [*]
    REJECTED --> [*]
    EXPIRED --> [*]
    FAILED --> [*]
```

A `FAILED` order has received the euros but no tokens were minted: in the demo it is resolved by hand, and the reconciliation panel shows it as an amount in transit.

### Redeem order

```mermaid
stateDiagram-v2
    [*] --> CREATED: client creates the order
    CREATED --> TOKENS_BURNED: RedeemConfirmed matches
    CREATED --> UNDER_REVIEW: RedeemConfirmed does not match
    CREATED --> EXPIRED: expires without on-chain redeem
    EXPIRED --> UNDER_REVIEW: late RedeemConfirmed
    TOKENS_BURNED --> COMPLETED: SEPA payout sent
    TOKENS_BURNED --> UNDER_REVIEW: bank rejects the payout
    COMPLETED --> [*]
    EXPIRED --> [*]
    UNDER_REVIEW --> [*]
```

If a `RedeemConfirmed` arrives for an order that is already `EXPIRED`, the tokens were burned anyway: the order goes to `UNDER_REVIEW` instead of being ignored.

## Data model

`SentTransaction`, `Checkpoint` and `ObservedEvent` live in `chain_db` (chain-service); everything else in `core_db` (core-api). Migrations: `core-api/src/main/resources/db/migration` and `chain-service/src/main/resources/db/migration`.

```mermaid
classDiagram
    direction LR
    class Wallet {
        address
        status: PENDING | VERIFIED
        verifiedAt
    }
    class Challenge {
        nonce
        address
        message
        expiresAt
        usedAt
    }
    class MintOrder {
        id
        amountCents
        sepaReference
        status
        idempotencyKey
        txHash
        createdAt
        expiresAt
    }
    class RedeemOrder {
        id
        onchainId: bytes32
        amountCents
        destinationIban
        status
        idempotencyKey
        txHash
        createdAt
        expiresAt
    }
    class BankTransfer {
        id
        direction: INCOMING | OUTGOING
        reference
        amountCents
        outcome
    }
    class LedgerEntry {
        id
        orderId
        debitAccount
        creditAccount
        amountCents
        bookedAt
    }
    class StateTransition {
        orderId
        fromStatus
        toStatus
        cause
        applied
        at
    }
    class OutboxEvent {
        eventId
        topic
        payload
        publishedAt
    }
    class ProcessedEvent {
        eventId
        processedAt
    }
    class SentTransaction {
        orderId
        nonce
        txHash
        status
    }
    class Checkpoint {
        lastProcessedBlock
    }
    class ObservedEvent {
        txHash
        logIndex
        blockNumber
        eventId
        publishedAt
    }
    Wallet "1" --> "*" Challenge
    Wallet "1" --> "*" MintOrder
    Wallet "1" --> "*" RedeemOrder
    MintOrder "1" --> "0..2" BankTransfer : payment + refund
    RedeemOrder "1" --> "0..1" BankTransfer : payout
    MintOrder "1" --> "0..1" LedgerEntry
    RedeemOrder "1" --> "0..1" LedgerEntry
    MintOrder "1" --> "*" StateTransition
    RedeemOrder "1" --> "*" StateTransition
```

Ledger (RF-CORE-16): two accounts, `BANK_RESERVE` and `EURD_CIRCULATION`. A completed mint debits the reserve and credits circulation; a redeem does the reverse. Entries are append-only (a database trigger rejects `UPDATE` and `DELETE`).

## Sequence diagrams

### CU-01 Connect and verify wallet

```mermaid
sequenceDiagram
    actor C as Client
    participant P as portal
    participant W as Wallet
    participant API as core-api
    participant DB as PostgreSQL
    C->>P: Connect wallet
    P->>W: Request accounts and network
    W-->>P: Address and chainId
    P->>API: POST /api/wallets/challenge {walletAddress}
    API->>DB: Store challenge with nonce and expiry
    API-->>P: Message to sign
    P->>W: Sign message (EIP-191)
    C->>W: Approves the signature
    W-->>P: Signature
    P->>API: POST /api/wallets/verify {walletAddress, nonce, signature}
    API->>API: Recover signer and validate nonce
    API->>DB: Mark nonce used and wallet VERIFIED
    API-->>P: 200 Wallet verified
    P-->>C: Verified status
```

### CU-02, CU-03 and CU-04 Request mint, pay and issue

```mermaid
sequenceDiagram
    actor C as Client
    participant P as portal
    participant API as core-api
    participant K as Kafka
    participant CS as chain-service
    participant SC as EURD contract
    C->>P: Amount to convert
    P->>API: POST /api/mint-orders (Idempotency-Key)
    API-->>P: Order CREATED, IBAN and reference
    P->>API: GET /api/orders/stream (SSE)
    C->>P: Simulate SEPA transfer
    P->>API: POST /api/bank-sim/incoming-transfers
    API->>API: Match reference and amount: PAID, APPROVED (+ outbox)
    API->>K: mint-orders.v1 MintApproved
    API-->>P: SSE: APPROVED
    K->>CS: MintApproved
    CS->>CS: Check there is no tx for the order
    CS->>SC: mint(wallet, amount, orderId)
    SC-->>CS: txHash
    CS->>K: chain-events.v1 MintSubmitted
    K->>API: MintSubmitted
    API-->>P: SSE: SUBMITTED with hash
    SC-->>CS: TokensEmitted in block
    CS->>CS: Wait N confirmations
    CS->>K: chain-events.v1 MintConfirmed
    K->>API: MintConfirmed
    API->>API: Ledger entry, COMPLETED
    API-->>P: SSE: COMPLETED
    P->>SC: balanceOf(wallet)
    P-->>C: New balance
```

### CU-06 and CU-07 Request and settle redeem

```mermaid
sequenceDiagram
    actor C as Client
    participant P as portal
    participant W as Wallet
    participant API as core-api
    participant SC as EURD contract
    participant CS as chain-service
    participant K as Kafka
    C->>P: Amount and IBAN
    P->>API: POST /api/redeem-orders (Idempotency-Key)
    API-->>P: Order CREATED with onchainId
    P->>W: Send redeem(amount, onchainId)
    C->>W: Approves the transaction
    W->>SC: redeem(amount, onchainId)
    SC-->>W: txHash
    SC-->>CS: TokensRedeemed in block
    CS->>CS: Wait N confirmations
    CS->>K: chain-events.v1 RedeemConfirmed
    K->>API: RedeemConfirmed
    API->>API: Validate holder and amount, TOKENS_BURNED
    API->>API: Ledger entry and simulated SEPA payout
    API->>API: COMPLETED
    API-->>P: SSE: COMPLETED
    P-->>C: Order completed and balance updated
```

### CU-08 Reconciliation

```mermaid
sequenceDiagram
    actor A as Technical admin
    participant P as portal
    participant API as core-api
    participant CS as chain-service
    participant SC as EURD contract
    A->>P: Open reconciliation panel
    P->>API: GET /api/reconciliation
    API->>API: Circulation according to the ledger
    API->>API: Simulated bank reserve and amounts in transit
    API->>CS: GET /chain/supply
    CS->>SC: totalSupply()
    SC-->>CS: Supply
    CS-->>API: Supply in cents and block
    API->>API: Compare and compute difference
    API-->>P: BALANCED or MISMATCH with difference
    P-->>A: Panel with totals and result
```
