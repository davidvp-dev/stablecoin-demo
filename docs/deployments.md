# Deployments

Where `EURDemo` is deployed on each network (RF-SC-14). Update this file after every deployment.

- Deploy script: [`contracts/script/DeployEURDemo.s.sol`](../contracts/script/DeployEURDemo.s.sol)
- Variables: `.env`, see [`.env.example`](../.env.example)
- Each run is recorded in `contracts/broadcast/DeployEURDemo.s.sol/<chain id>/run-latest.json` (git-ignored, local only).

## Anvil (local, chain ID 31337)

| | |
|---|---|
| Contract | `0x5FbDB2315678afecb367f032d93F642f64180aa3` |
| Deployed | 2026-11-26, block 1 (first deployment on the persistent chain) |
| Transaction | `0x003b653d3f9a56d1fd7100be527df0f65e2983c7bd64465f909b3c171715c07f` |
| Commit | `5cce711` |
| Admin (deployer, `DEFAULT_ADMIN_ROLE`) | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` (Anvil account #0) |
| `MINTER_ROLE` | `0x70997970C51812dc3A010C7d01b50e0d17dc79C8` (Anvil account #1, chain-service: `MINTER_PRIVATE_KEY_ANVIL`) |
| `PAUSER_ROLE` | `0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC` (Anvil account #2) |
| `BLACKLISTER_ROLE` | `0x90F79bf6EB2c4f870365E785982E1f101E93b906` (Anvil account #3) |
| `.env` variable | `EURD_CONTRACT_ADDRESS_ANVIL` |

Each role is held by a different account, as set in `.env` (`MINTER_ADDRESS_ANVIL`, `PAUSER_ADDRESS_ANVIL`, `BLACKLISTER_ADDRESS_ANVIL`). The 2026-11-07 deployment had every role on account #0.

**The Anvil chain is persistent.** Anvil runs with `--state=/home/foundry/state.json` on the `anvil-state` volume (see `docker-compose.yml`): it loads the chain on startup and saves it on exit and every 5 seconds. The contract, balances and block number survive a container restart, `docker compose stop` / `up -d`, and closing the laptop. If the container is killed without a clean exit, at most the last 5 seconds are lost.

The chain only starts empty again after `docker compose down -v`, which deletes the volume (and Postgres's data with it, so both stay consistent). Then the contract has to be deployed again:

```bash
cd contracts
forge script script/DeployEURDemo.s.sol --rpc-url anvil --broadcast
```

To check whether it's deployed without looking at the logs: `GET http://localhost:8081/chain/health` on chain-service returns `contractDeployed: false` when the contract is missing.

On a fresh chain the address is the same every time, because it only depends on the deployer address and its nonce (0).
To check the contract is there: `cast call 0x5FbDB2315678afecb367f032d93F642f64180aa3 "name()(string)" --rpc-url anvil` returns `"Euro Demo"`.

## Sepolia (testnet, chain ID 11155111)

Not deployed with the current version yet.

| | |
|---|---|
| Contract | — |
| Deployed | — (block → `EURD_DEPLOY_BLOCK_SEPOLIA`) |
| Transaction | — |
| Etherscan | — |
| Commit | — |
| Admin (deployer, `DEFAULT_ADMIN_ROLE`) | — |
| `MINTER_ROLE` | — (chain-service: `MINTER_PRIVATE_KEY_SEPOLIA`) |
| `PAUSER_ROLE` | — |
| `BLACKLISTER_ROLE` | — |
| `.env` variables | `EURD_CONTRACT_ADDRESS_SEPOLIA`, `EURD_DEPLOY_BLOCK_SEPOLIA` |

**Before deploying:** fill the `*_SEPOLIA` variables and `ETHERSCAN_API_KEY` in `.env` (see `.env.example`), and fund the deployer and the minter with Sepolia ETH.

```bash
cd contracts
# 1. Dry run: runs the script against Sepolia's state and checks the roles, sends nothing
forge script script/DeployEURDemo.s.sol --rpc-url sepolia
# 2. Deploy and verify on Etherscan. --skip-simulation is required (see below)
forge script script/DeployEURDemo.s.sol --rpc-url sepolia --broadcast --verify --skip-simulation
```

**Why `--skip-simulation`.** Without it, forge sets each transaction's gas limit from its own local simulation, which uses the gas costs of `evm_version` in `foundry.toml` (`prague`) × 1.3. Sepolia now runs a hardfork that charges much more for creating state, so that limit is far too low. Measured with `eth_estimateGas` on 2026-11-28:

| | Sepolia | `prague` (forge's simulation) |
|---|---|---|
| Empty `CREATE` | ~210,000 gas | 53,000 |
| Each byte of deployed code | ~1,540 gas | 200 |
| `SSTORE` to a new slot | ~111,000 gas | ~22,100 |
| Deploying `EURDemo` | ~8.4 M gas | ~1.25 M |

The first attempt (without the flag) sent 1,625,829 gas, ran out, and left an address with no code (`0x`) and a reverted transaction; see Obsolete deployments. With `--skip-simulation`, forge asks the node (`eth_estimateGas`) and the limit is right. Which hardfork and EIP changed the costs is not confirmed here: check Sepolia's active fork on forkcast.org or in `ethereum/execution-specs`.

The same costs apply to `mint()` from chain-service: a mint to a new holder writes new slots (balance and `orderId`), so it needs well over the ~102k gas it uses on Anvil. Check `app.chain.gas.gasLimit` in `chain-service/src/main/resources/application-sepolia.properties` against `cast estimate <contract> "mint(address,uint256,bytes32)" <to> <units> <orderId> --from <minter> --rpc-url sepolia`.

The address, transaction and block are in `contracts/broadcast/DeployEURDemo.s.sol/11155111/run-latest.json`.

**Running the backends against Sepolia** uses the `sepolia` Spring profile (`application-sepolia.properties` in core-api and chain-service):

```bash
SPRING_PROFILES_ACTIVE=sepolia ./mvnw spring-boot:run   # in core-api/ and in chain-service/
```

- Each network has its own databases (`core_db_sepolia`, `chain_db_sepolia`), so orders, ledger and checkpoints never mix. Kafka topics and consumer groups are shared: before switching network, check in Kafka UI that the `core-api` and `chain-service` groups have no lag, or the other network's pending messages are processed on the new one.
- chain-service waits for 3 confirmations and reads every 12 s.
- `GET http://localhost:8081/chain/health` must return `UP` with `chainId: 11155111`.

### Obsolete deployments

| Address | Date | Commit | Why it's obsolete |
|---|---|---|---|
| `0x3a372970640fc651443aead9ecd90e2571e64de5` | 2026-10-28 | `fcf3f30` | Old one-argument constructor, no `BLACKLISTER_ROLE`, old ABI. Don't use it. |
| `0xd5d219223afaf5ebf7bab7e0a2b1d8170a9ababf` | 2026-11-28 | `c80ed14` | Failed deployment: out of gas (tx `0x121e340383c16c37c8293b43a4676fd221dc88bf3ff9487e65fdf74bb83a2456`, block 12229163, `status 0`). No code at the address. Deployed without `--skip-simulation`. |
