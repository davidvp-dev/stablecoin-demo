# stablecoin-demo

## Objective

**EURD** is a demo of a fiat-backed euro stablecoin. Paying **1 EUR** by (simulated) SEPA bank transfer gets you **1 EURD** on-chain, and redeeming (burning) EURD sends the euros back to the user's bank account.

The project copies, at a small scale, how a real e-money token issuer works:

- **On-chain token:** an ERC-20 with 6 decimals, role-based access control, pausing, a blacklist, and order IDs that stop the same mint or redeem from running twice.
- **Off-chain backend:** wallet ownership verification (Sign-In with Ethereum, ERC-4361), mint and redeem orders, a simulated bank, and a double-entry ledger.
- **Reconciliation:** checks that the euros held in the bank reserve, the ledger and the on-chain `totalSupply` always match, so the token stays fully backed.
- **Event-driven integration:** the backend and the blockchain service talk through Kafka, and processing is idempotent because Kafka can deliver the same event more than once.

### End-to-end flow

1. **Wallet verification:** the portal requests a SIWE challenge from `core-api`, the user signs it, and the verified address is whitelisted.
2. **Mint:** the user creates a mint order, the simulated bank receives the SEPA transfer, `core-api` sends an event to `chain-service`, and `chain-service` calls `mint()` on the contract. The `TokensEmitted` event confirms the order (`CREATED → PAID → APPROVED → SUBMITTED → COMPLETED`).
3. **Redeem:** the user calls `redeem()` on-chain, `chain-service` picks up the `TokensRedeemed` event, and `core-api` sends the payout from the simulated bank.
4. **Accounting:** every movement is recorded in an append-only double-entry ledger, and `/reconciliation` compares the bank, the ledger and the chain.

## Documentation

- [`docs/architecture.md`](docs/architecture.md): components, order state machines, data model and sequence diagrams
- [`docs/events.md`](docs/events.md): Kafka topics, event envelope and conversion rules (cents ↔ units, UUID ↔ bytes32)
- [`docs/deployments.md`](docs/deployments.md): contract addresses on Anvil and Sepolia, with the roles granted
- [`core-api` OpenAPI](core-api/src/main/resources/api-definition.yml) and [`chain-service` OpenAPI](chain-service/src/main/resources/api-definition.yml): REST contracts

## Repository structure

```
stablecoin-demo/
├── contracts/          # Foundry project: EURDemo.sol (ERC-20 stablecoin), tests and deploy script
├── core-api/           # Spring Boot (Java 25): REST API (OpenAPI-first), wallets, orders, bank sim, ledger, reconciliation
├── chain-service/      # Spring Boot (Java 25): holds MINTER_ROLE, sends transactions and listens to contract events
├── portal/             # React + Vite + wagmi/viem: user web app (connect wallet, sign in, mint/redeem)
├── infra/postgres/     # SQL init scripts (creates core_db and chain_db)
├── docs/              # Architecture (Mermaid diagrams, data model, state machines) and Kafka event contract
├── docker-compose.yml  # Local infrastructure: PostgreSQL, Kafka (KRaft), Kafka UI, Anvil
└── .env.example        # Environment variables template
```

| Component | Tech | Responsibility |
|---|---|---|
| `contracts` | Solidity 0.8.34, Foundry, OpenZeppelin | `EURDemo` token: `mint`, `redeem`, `pause`, blacklist |
| `core-api` | Spring Boot, PostgreSQL, Flyway, Kafka, web3j | Business logic and source of truth for orders and the ledger |
| `chain-service` | Spring Boot, PostgreSQL, Kafka | Bridge between the backend and the blockchain |
| `portal` | React, Vite, wagmi, viem | Frontend for end users |

### Local infrastructure (docker-compose)

| Service | URL / Port |
|---|---|
| PostgreSQL | `localhost:5433` |
| Kafka | `localhost:9092` |
| Kafka UI | http://localhost:8090 |
| Anvil (local Ethereum, chain ID 31337) | http://localhost:8545 |

## Getting started

1. **Clone with submodules:**

   ```bash
   git clone --recursive <repo-url>
   ```

   `forge install` adds the Solidity libraries (forge-std, OpenZeppelin, …) as git submodules. In a monorepo they are registered in the root `.gitmodules`, so `--recursive` is needed to download them.

2. **Create the `.env` file** in the root from `.env.example`. Foundry also needs `PRIVATE_KEY_ANVIL`, `PRIVATE_KEY_SEPOLIA_WALLET`, `ETHERSCAN_API_KEY`, `RPC_URL_ANVIL` and `RPC_URL_SEPOLIA`.

3. **Link the `.env` into `contracts/`:**

   ```bash
   cd contracts && ln -s ../.env .env
   ```

   This makes `contracts/.env` the same file as the root one: edit either and both change, and Foundry finds it without any extra configuration.

4. **Start the local infrastructure:**

   ```bash
   docker compose up -d
   ```

5. **Deploy the contract:**

   ```bash
   cd contracts
   forge script script/DeployEURDemo.s.sol --rpc-url anvil --broadcast            # local Anvil
   forge script script/DeployEURDemo.s.sol --rpc-url sepolia --broadcast --verify # Sepolia testnet
   ```

## Production solutions out of scope

These are solutions a real system would use. They are not part of this project, but they are worth mentioning:

1. **Signed redeem authorizations (EIP-712):** to prevent griefing on `redeem`, `core-api` would sign an EIP-712 message such as *"user U may redeem amount N with order X before time T"*. Before burning, the contract would check that the signature comes from an address with an authorized role (for example, a `REDEEM_SIGNER_ROLE`).

## Technical issues solved

1. **Sepolia deployment failure:** the deployment kept failing because the tooling simulated transactions with gas rules from before the testnet's hard fork. I found the cause by comparing the local simulation with the network's `eth_estimateGas`.
