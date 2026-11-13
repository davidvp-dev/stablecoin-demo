# Kafka event contract (v1)

This file is the contract between **core-api** and **chain-service**. It is written before the code; any breaking change creates a new topic version (`*.v2`) instead of modifying these ones.

## Topics

| Topic | Producer | Consumer | Events | Key |
|---|---|---|---|---|
| `mint-orders.v1` | core-api | chain-service | `MintApproved` | order id |
| `chain-events.v1` | chain-service | core-api | `MintSubmitted`, `MintConfirmed`, `MintFailed`, `RedeemConfirmed` | order id |

- **Key = order id** (UUID string): every event of the same order goes to the same partition, so it is consumed in order.
- **Delivery is at least once.** Consumers must be idempotent: each `eventId` is processed exactly once (`processed_events` in core-api, `sent_transactions` in chain-service).
- Topics are created explicitly at startup (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`): 3 partitions, replication factor 1 in local.
- Messages are JSON strings (UTF-8), serialized with `StringSerializer`.

## Envelope

Every event shares the same envelope:

```json
{
  "eventId": "7f9c2b1e-4a3d-4c8e-9b21-0d5e6f7a8b90",
  "eventType": "MintConfirmed",
  "version": 1,
  "occurredAt": "2026-11-12T10:15:30Z",
  "orderId": "9b2e4c1a-7d3f-4e8b-9a2c-5d6e7f8a9b0c",
  "payload": {
    "wallet": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
    "amountCents": 125000,
    "txHash": "0x5d1c6f2a9e0b7d3c4a8f1e2b6c9d0a7e3f5b8c1d2e4f6a9b0c3d5e7f8a1b2c3d",
    "blockNumber": 7012345,
    "confirmations": 3
  }
}
```

| Field | Type | Description |
|---|---|---|
| `eventId` | UUID | Unique id of the event; the idempotency key for consumers |
| `eventType` | string | One of the event names below |
| `version` | integer | Payload version, `1` for this contract |
| `occurredAt` | ISO-8601 UTC | When the fact happened (not when it was published) |
| `orderId` | UUID | core-api order id; same value as the Kafka key. `null` only for a redeem with an unknown on-chain id (see conversion rules) |
| `payload` | object | Event-specific fields |

### How `eventId` is generated

- **core-api** generates a random UUID (v4) when it writes the event to the outbox; the outbox row is published as is, so retries keep the same `eventId`.
- **chain-service** must derive it **deterministically** so a republish after a crash has the same id:
  - `MintSubmitted` / `MintFailed`: UUID v5 of `"<eventType>:<orderId>"`.
  - `MintConfirmed` / `RedeemConfirmed`: UUID v5 of `"<chainId>:<txHash>:<logIndex>"`.

## Events

| Event | Topic | Published when | Payload fields |
|---|---|---|---|
| `MintApproved` | `mint-orders.v1` | A mint order is paid and auto-approved (status `APPROVED`) | `wallet`, `amountCents` |
| `MintSubmitted` | `chain-events.v1` | The node accepts the `mint` transaction | `txHash`, `nonce` |
| `MintConfirmed` | `chain-events.v1` | The `TokensEmitted` event reaches N confirmations | `wallet`, `amountCents`, `txHash`, `blockNumber`, `confirmations` |
| `MintFailed` | `chain-events.v1` | The transaction reverts or retries are exhausted | `reason`, `txHash` (if any) |
| `RedeemConfirmed` | `chain-events.v1` | The `TokensRedeemed` event reaches N confirmations | `holder`, `amountCents`, `onchainId`, `txHash`, `blockNumber`, `confirmations` |

Payload field types: addresses are EIP-55 checksum strings, `amountCents` and `blockNumber` are integers, `txHash` is a `0x`-prefixed 32-byte hex string, `nonce` and `confirmations` are integers, `reason` is a human-readable string.

### Effect on core-api (RF-CORE-11, 13)

| Event | Mint order transition | Redeem order transition |
|---|---|---|
| `MintSubmitted` | `APPROVED → SUBMITTED` (stores `txHash`) | — |
| `MintConfirmed` | `SUBMITTED → COMPLETED` (+ ledger entry) | — |
| `MintFailed` | `APPROVED/SUBMITTED → FAILED` (stores reason) | — |
| `RedeemConfirmed` | — | `CREATED → TOKENS_BURNED` if holder and amount match, otherwise `→ UNDER_REVIEW`; `EXPIRED → UNDER_REVIEW` |

Any other transition is rejected and stored with `applied = false` (RF-CORE-22).

## Conversion rules (RF-CHAIN-02)

Both rules live **only** in chain-service and the portal; core-api always works in cents.

### Amount: cents ↔ token units

The contract has 6 decimals, so **1 cent = 10,000 units**.

```
units = amountCents × 10_000        12,34 € = 1234 cents = 12,340,000 units
amountCents = units / 10_000        (units not divisible by 10,000 are rejected as invalid)
```

### Order id: UUID ↔ bytes32

The UUID's 16 bytes are **right-aligned** in the 32-byte word (left-padded with zeros), i.e. the bytes32 is the UUID read as a `uint256`:

```
UUID     9b2e4c1a-7d3f-4e8b-9a2c-5d6e7f8a9b0c
bytes32  0x000000000000000000000000000000009b2e4c1a7d3f4e8b9a2c5d6e7f8a9b0c
```

The rule is reversible, so chain-service turns an on-chain `orderId` back into the core-api UUID without any lookup (RF-CHAIN-09). A bytes32 whose first 16 bytes are not zero cannot belong to an order of the system (for example, someone called `redeem()` directly with a made-up id). The burn still happened, so chain-service still publishes `RedeemConfirmed`, with `orderId: null`, the raw value in `payload.onchainId` and the Kafka key set to that bytes32. core-api records it for manual review instead of dropping it (RF-CORE-15).

The same rule produces the `onchainId` returned by `POST /api/redeem-orders`.
