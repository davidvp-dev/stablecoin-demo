// SPDX-License-Identifier: MIT

pragma solidity 0.8.34;

import {Test} from "forge-std/Test.sol";
import {EURDemo} from "../../src/EURDemo.sol";
import {EURDemoHandler} from "./EURDemoHandler.sol";

/**
 * @dev Invariant tests for EURDemo (RF-SC-13).
 *
 *      An invariant is a rule that must hold no matter what happens. Foundry calls random handler functions in random
 *      sequences, and after every call it runs every `invariant_*` function below. If one assertion ever fails,
 *      Foundry prints the sequence of calls that broke it.
 */
contract EURDemoInvariantTest is Test {
    EURDemo internal eurDemo;
    EURDemoHandler internal handler;

    address internal admin = makeAddr("admin");
    address internal minter = makeAddr("minter");
    address internal pauser = makeAddr("pauser");
    address internal blacklister = makeAddr("blacklister");

    function setUp() public {
        // 1. Deploy the token as the admin, with one account per role.
        vm.prank(admin);
        eurDemo = new EURDemo(minter, pauser, blacklister);

        // 2. Deploy the handler, which calls the token for us.
        handler = new EURDemoHandler(eurDemo, minter);

        // 3. Tell Foundry to call only the handler, not the token directly.
        targetContract(address(handler));

        // 4. And only these handler functions (the handler also inherits many helpers from forge-std's Test).
        //    When you add pause, blacklist, etc. to the handler, add their selectors here too.
        bytes4[] memory selectors = new bytes4[](3);
        selectors[0] = EURDemoHandler.mint.selector;
        selectors[1] = EURDemoHandler.redeem.selector;
        selectors[2] = EURDemoHandler.transfer.selector;
        targetSelector(FuzzSelector({addr: address(handler), selectors: selectors}));
    }

    /// @dev Invariant 1 (RF-SC-13): the supply is exactly what was minted minus what was burned.
    function invariant_supplyEqualsMintedMinusBurned() public view {
        assertEq(eurDemo.totalSupply(), handler.ghostMinted() - handler.ghostBurned());
    }

    /// @dev Invariant 2: tokens only exist in the users' wallets, so their balances add up to the supply.
    function invariant_balancesSumToSupply() public view {
        // Add up every user's balance...
        uint256 sum;
        for (uint256 i = 0; i < handler.actorsLength(); i++) {
            sum += eurDemo.balanceOf(handler.actors(i));
        }

        // ...and compare it with the supply.
        assertEq(sum, eurDemo.totalSupply());
    }

    /// @dev Invariant 3 (RF-SC-09): a blacklisted user can't send, receive or redeem, so their balance never changes.
    function invariant_blacklistedBalanceFrozen() public view {
        // TODO: for every actor that is blacklisted, assert their balance equals the one saved in handler.blacklist().
    }

    /// @dev Invariant 4 (RF-SC-08): while paused, no mint, redeem or transfer goes through.
    function invariant_pausedStateFrozen() public view {
        // TODO: if the contract is paused, assert the supply and every balance equal the ones saved in handler.pause().
    }

    /// @dev Invariant 5 (RF-SC-04, RF-SC-06): an order id can only succeed once.
    function invariant_orderIdsUsedOnce() public view {
        // TODO: assert the number of successful mints equals the number of unique order ids the handler used.
    }
}
