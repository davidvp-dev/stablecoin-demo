// SPDX-License-Identifier: MIT

pragma solidity 0.8.34;

import {Test} from "forge-std/Test.sol";
import {EURDemo} from "../../src/EURDemo.sol";

/**
 * @dev Handler for the EURDemo invariant tests (RF-SC-13).
 *
 *      How it works:
 *      - Foundry calls the handler functions below in a random order, with random arguments.
 *      - Each function turns those random arguments into a valid call (a real user, an amount they can afford, a new
 *        order id) and makes the call on the token.
 *      - After the call succeeds, the handler updates its "ghost" variables: simple running totals that the invariant
 *        test compares against the real contract state.
 *      - If a call reverts, Foundry undoes the whole handler call, ghost updates included, so the totals stay correct.
 */
contract EURDemoHandler is Test {
    EURDemo public immutable eurDemo;
    address public immutable minter;

    /// @dev A fixed list of users, so the invariant test can loop over every holder.
    address[] public actors;

    /// @dev Ghost variables: everything ever minted and everything ever burned.
    uint256 public ghostMinted;
    uint256 public ghostBurned;

    /// @dev Counter used to build a new order id on every call, so mint and redeem never revert for a reused id.
    uint256 internal orderNonce;

    /// @dev Upper bound for one mint: realistic, and low enough that the totals never overflow.
    uint256 internal constant MAX_MINT_AMOUNT = 1e18;

    constructor(EURDemo eurDemo_, address minter_) {
        eurDemo = eurDemo_;
        minter = minter_;

        // Three users are enough to get transfers between different holders.
        actors.push(makeAddr("alice"));
        actors.push(makeAddr("bob"));
        actors.push(makeAddr("carol"));
    }

    /**
     * @dev Mint a random amount to a random user.
     */
    function mint(uint256 actorSeed, uint256 amount) external {
        // 1. Turn the random seed into one of the users.
        address to = _actor(actorSeed);

        // 2. Keep the amount between 1 and the maximum (0 would revert with ZeroAmount).
        amount = bound(amount, 1, MAX_MINT_AMOUNT);

        // 3. Build a new order id for this call.
        bytes32 orderId = keccak256(abi.encode("MINT", orderNonce++));

        // 4. Only the minter can mint, so make the call as the minter.
        vm.prank(minter);
        eurDemo.mint(to, amount, orderId);

        // 5. The mint succeeded: add the amount to the minted total.
        ghostMinted += amount;
    }

    /**
     * @dev A random user redeems (burns) part of their balance.
     */
    function redeem(uint256 actorSeed, uint256 amount) external {
        // 1. Turn the random seed into one of the users.
        address account = _actor(actorSeed);

        // 2. A user with no tokens can't redeem anything, so skip this call.
        uint256 balance = eurDemo.balanceOf(account);
        if (balance == 0) return;

        // 3. Keep the amount between 1 and what the user holds.
        amount = bound(amount, 1, balance);

        // 4. Build a new order id for this call.
        bytes32 orderId = keccak256(abi.encode("REDEEM", orderNonce++));

        // 5. The user burns their own tokens, so make the call as the user.
        vm.prank(account);
        eurDemo.redeem(amount, orderId);

        // 6. The redeem succeeded: add the amount to the burned total.
        ghostBurned += amount;
    }

    /**
     * @dev A random user sends tokens to another random user (it can be themselves).
     */
    function transfer(uint256 fromSeed, uint256 toSeed, uint256 amount) external {
        // 1. Turn the random seeds into a sender and a receiver.
        address from = _actor(fromSeed);
        address to = _actor(toSeed);

        // 2. Keep the amount between 0 and what the sender holds.
        amount = bound(amount, 0, eurDemo.balanceOf(from));

        // 3. Make the call as the sender. A transfer doesn't change the supply, so no ghost variable to update.
        vm.prank(from);
        eurDemo.transfer(to, amount);
    }

    // TODO: pause() and unpause()
    //   - Call eurDemo.pause() / unpause() as the pauser (pass the pauser address in the constructor).
    //   - Pausing an already paused contract reverts, so check eurDemo.paused() first.
    //   - In pause(), save the totalSupply and every actor's balance, for invariant_pausedStateFrozen.
    //   - Your mint/redeem/transfer will then revert while paused; that's fine, nothing changes.

    // TODO: blacklist() and removeFromBlacklist()
    //   - Pick a random actor and call as the blacklister.
    //   - In blacklist(), save the actor's balance, for invariant_blacklistedBalanceFrozen.

    // TODO: reuse order ids
    //   - Add a flag to mint() that retries an order id that already succeeded, instead of a new one.
    //   - Count successful mints and unique order ids, for invariant_orderIdsUsedOnce.

    // ─── Helpers for the invariant test ───

    function actorsLength() external view returns (uint256) {
        return actors.length;
    }

    /// @dev Maps any random number to one of the users.
    function _actor(uint256 seed) internal view returns (address) {
        return actors[seed % actors.length];
    }
}
