// SPDX-License-Identifier: MIT

pragma solidity 0.8.34;

import {Test} from "forge-std/Test.sol";
import {EURDemo} from "../src/EURDemo.sol";
import {ERC20} from "../lib/openzeppelin-contracts/contracts/token/ERC20/ERC20.sol";

/**
 * @dev EUR Demo Test suite covering the functional requirements, fuzz testing, and user cases.
 */
contract EURDemoTest is Test {
    EURDemo eurDemo;
    address public minter = makeAddr("minter");
    address public admin = makeAddr("admin");
    address public pauser = makeAddr("pauser");
    address public blacklister = makeAddr("blacklister");
    address public user = makeAddr("user");

    function setUp() public {
        eurDemo = new EURDemo(minter, pauser, blacklister);
    }
}
