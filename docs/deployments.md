# Deployments

Where `EURDemo` is deployed on each network (RF-SC-14). Update this file after every deployment.

- Deploy script: [`contracts/script/DeployEURDemo.s.sol`](../contracts/script/DeployEURDemo.s.sol)
- Variables: `.env`, see [`.env.example`](../.env.example)
- Each run is recorded in `contracts/broadcast/DeployEURDemo.s.sol/<chain id>/run-latest.json` (git-ignored, local only).

## Anvil (local, chain ID 31337)

| | |
|---|---|
| Contract | `0x5FbDB2315678afecb367f032d93F642f64180aa3` |
| Deployed | 2026-11-07, block 80 |
| Transaction | `0xfe88cdfc591ce3be31dae877c6d21bfda837ea505a78edb58c1c295c8840de0a` |
| Commit | `22d3d96` |
| Admin (deployer, `DEFAULT_ADMIN_ROLE`) | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` (Anvil account #0) |
| `MINTER_ROLE` | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` (Anvil account #0) |
| `PAUSER_ROLE` | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` (Anvil account #0) |
| `BLACKLISTER_ROLE` | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` (Anvil account #0) |
| `.env` variable | `EURD_CONTRACT_ADDRESS_ANVIL` |

**The Anvil container doesn't persist its state.** After `docker compose down` or a container restart, the chain starts empty and the contract has to be deployed again:

```bash
cd contracts
forge script script/DeployEURDemo.s.sol --rpc-url anvil --broadcast
```

On a fresh chain the address is the same every time, because it only depends on the deployer address and its nonce (0).
To check the contract is there: `cast call 0x5FbDB2315678afecb367f032d93F642f64180aa3 "name()(string)" --rpc-url anvil` returns `"Euro Demo"`.

## Sepolia (testnet, chain ID 11155111)

Not deployed with the current version yet.

| | |
|---|---|
| Contract | — |
| Etherscan | — |
| `.env` variable | `EURD_CONTRACT_ADDRESS_SEPOLIA` |

```bash
cd contracts
forge script script/DeployEURDemo.s.sol --rpc-url sepolia --broadcast --verify
```

### Obsolete deployments

| Address | Date | Commit | Why it's obsolete |
|---|---|---|---|
| `0x3a372970640fc651443aead9ecd90e2571e64de5` | 2026-10-28 | `fcf3f30` | Old one-argument constructor, no `BLACKLISTER_ROLE`, old ABI. Don't use it. |
