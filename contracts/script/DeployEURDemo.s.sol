// SPDX-License-Identifier: MIT
// forge script script/DeployEURDemo.s.sol anvil --broadcast (LOCAL ANVIL FORK)
// forge script script/DeployEURDemo.s.sol sepolia --broadcast --verify (SEPOLIA TESTNET)
pragma solidity 0.8.34;

import {Script} from "forge-std/Script.sol";
import {EURDemo} from "../src/EURDemo.sol";

contract DeployEURDemo is Script {
    function run() external returns (EURDemo) {
        uint256 deployerPrivateKey = vm.envUint("PRIVATE_KEY_ANVIL");
        address admin = 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266; //anvil
        // address admin = 0xD5C7C9f4E570a489cD5BF3CC676B0bD020EBa40f; sepolia

        vm.startBroadcast(deployerPrivateKey);
        EURDemo eurDemo = new EURDemo(admin);
        vm.stopBroadcast();
        return eurDemo;
    }
}
