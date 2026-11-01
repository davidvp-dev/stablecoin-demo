// SPDX-License-Identifier: MIT
pragma solidity 0.8.34;

import {Script, console} from "forge-std/Script.sol";
import {EURDemo} from "../src/EURDemo.sol";

/**
 * @title Deploy EURDemo
 * @notice Deploys EURDemo to Anvil or Sepolia. The network is chosen with --rpc-url and detected from the chain id;
 *         keys and role addresses are read from .env with the network suffix (_ANVIL or _SEPOLIA).
 *
 *   Anvil:   forge script script/DeployEURDemo.s.sol --rpc-url anvil --broadcast
 *   Sepolia: forge script script/DeployEURDemo.s.sol --rpc-url sepolia --broadcast --verify
 *
 *   Required variables (see .env.example):
 *     PRIVATE_KEY_<NETWORK>          deployer key; the deployer becomes DEFAULT_ADMIN_ROLE
 *     MINTER_ADDRESS_<NETWORK>       chain-service account (MINTER_ROLE)
 *     PAUSER_ADDRESS_<NETWORK>       technical admin account (PAUSER_ROLE)
 *     BLACKLISTER_ADDRESS_<NETWORK>  technical admin account (BLACKLISTER_ROLE)
 */
contract DeployEURDemo is Script {
    uint256 internal constant ANVIL_CHAIN_ID = 31337;
    uint256 internal constant SEPOLIA_CHAIN_ID = 11155111;

    error UnsupportedChain(uint256 chainId);
    error ZeroRoleAddress(string variable);
    error RoleNotGranted(string role);

    struct DeployConfig {
        uint256 deployerPrivateKey;
        address minter;
        address pauser;
        address blacklister;
    }

    function run() external returns (EURDemo eurDemo) {
        DeployConfig memory config = loadConfig();
        address admin = vm.addr(config.deployerPrivateKey);

        vm.startBroadcast(config.deployerPrivateKey);
        eurDemo = new EURDemo(config.minter, config.pauser, config.blacklister);
        vm.stopBroadcast();

        checkRoles(eurDemo, admin, config);

        console.log("Network chain id:  ", block.chainid);
        console.log("EURDemo deployed:  ", address(eurDemo));
        console.log("Admin (deployer):  ", admin);
        console.log("Minter:            ", config.minter);
        console.log("Pauser:            ", config.pauser);
        console.log("Blacklister:       ", config.blacklister);
    }

    /// @notice Reads the variables of the network the script is running against.
    function loadConfig() public view returns (DeployConfig memory config) {
        string memory suffix = networkSuffix();

        config.deployerPrivateKey = vm.envUint(string.concat("PRIVATE_KEY_", suffix));
        config.minter = readRoleAddress(string.concat("MINTER_ADDRESS_", suffix));
        config.pauser = readRoleAddress(string.concat("PAUSER_ADDRESS_", suffix));
        config.blacklister = readRoleAddress(string.concat("BLACKLISTER_ADDRESS_", suffix));
    }

    function networkSuffix() internal view returns (string memory) {
        if (block.chainid == ANVIL_CHAIN_ID) return "ANVIL";
        if (block.chainid == SEPOLIA_CHAIN_ID) return "SEPOLIA";
        revert UnsupportedChain(block.chainid);
    }

    function readRoleAddress(string memory variable) internal view returns (address account) {
        account = vm.envAddress(variable);
        if (account == address(0)) revert ZeroRoleAddress(variable);
    }

    /// @dev Fails the script if the deployed contract does not hold exactly the expected roles (RF-SC-10).
    function checkRoles(EURDemo eurDemo, address admin, DeployConfig memory config) internal view {
        if (!eurDemo.hasRole(eurDemo.DEFAULT_ADMIN_ROLE(), admin)) revert RoleNotGranted("DEFAULT_ADMIN_ROLE");
        if (!eurDemo.hasRole(eurDemo.MINTER_ROLE(), config.minter)) revert RoleNotGranted("MINTER_ROLE");
        if (!eurDemo.hasRole(eurDemo.PAUSER_ROLE(), config.pauser)) revert RoleNotGranted("PAUSER_ROLE");
        if (!eurDemo.hasRole(eurDemo.BLACKLISTER_ROLE(), config.blacklister)) {
            revert RoleNotGranted("BLACKLISTER_ROLE");
        }
    }
}
