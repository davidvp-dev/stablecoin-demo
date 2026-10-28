// SPDX-License-Identifier: MIT

pragma solidity 0.8.34;

import {Test} from "forge-std/Test.sol";
import {EURDemo} from "../src/EURDemo.sol";
import {ERC20} from "../lib/openzeppelin-contracts/contracts/token/ERC20/ERC20.sol";
import {AccessControl} from "../lib/openzeppelin-contracts/contracts/access/AccessControl.sol";
import {Pausable} from "../lib/openzeppelin-contracts/contracts/utils/Pausable.sol";

error ZeroAmount();
error ZeroAddress();
error MintOrderAlreadySubmitted(bytes32);
error RedeemOrderAlreadySubmitted(bytes32);
error UserBlacklisted();

/**
 * @dev EUR Demo Test suite covering the functional requirements, fuzz testing, and user cases.
 */
contract EURDemoTest is AccessControl, Pausable, Test {
    EURDemo eurDemo;
    address public minter = makeAddr("minter");
    address public admin = makeAddr("admin");
    address public pauser = makeAddr("pauser");
    address public user = makeAddr("user");

    function setUp() public {
        eurDemo = new EURDemo(minter);
    }
}
