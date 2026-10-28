// SPDX-License-Identifier: MIT

pragma solidity 0.8.34;

import {ERC20} from "../lib/openzeppelin-contracts/contracts/token/ERC20/ERC20.sol";
import {AccessControl} from "../lib/openzeppelin-contracts/contracts/access/AccessControl.sol";
import {Pausable} from "../lib/openzeppelin-contracts/contracts/utils/Pausable.sol";

error ZeroAmount();
error ZeroAddress();
error MintOrderAlreadySubmitted(bytes32);
error RedeemOrderAlreadySubmitted(bytes32);
error UserBlacklisted();

/**
 * @title EUR Demo Stablecoin Contract
 * @author David Villalgordo
 * @notice 6 decimals token based in ERC-20 standard and role-access control to mint and redeem 1EUR to 1EURD tokens through SEPA transfers
 */
contract EURDemo is ERC20, AccessControl, Pausable {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE"); // belongs to chain service
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");
    uint8 public constant EURD_DECIMALS = 6;

    mapping(address => bool) public isBlacklisted;
    mapping(bytes32 => bool) public isMintOrderSubmitted;
    mapping(bytes32 => bool) public isRedeemOrderSubmitted;

    constructor(address minterRoleAddress_) ERC20("EUR Demo", "EURD") {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(MINTER_ROLE, minterRoleAddress_);
        _grantRole(PAUSER_ROLE, msg.sender);
    }

    event TokensEmitted(address indexed to, uint256 amount, bytes32 orderId);
    event TokensRedeemed(address indexed account, uint256 amount, bytes32 orderId);
    event BlacklistStatusChanged(address indexed account, bool isBlacklisted);
    event PausedContract();
    event UnpausedContract();

    /**
     * @dev This function mints tokens only allowed by an allowed minter and when the contract is not paused.
     * @param to_ user receiving the tokens
     * @param amount_ amount of tokens
     * @param orderId_ order id that must be unique
     */
    function mint(address to_, uint256 amount_, bytes32 orderId_) external onlyRole(MINTER_ROLE) whenNotPaused {
        if (to_ == address(0)) revert ZeroAddress();
        if (amount_ == 0) revert ZeroAmount();
        if (isBlacklisted[to_]) revert UserBlacklisted();
        if (isMintOrderSubmitted[orderId_]) revert MintOrderAlreadySubmitted(orderId_);

        _mint(to_, amount_);
        emit TokensEmited(to_, amount_, orderId_);
    }

    /**
     * @dev This function allows the user to redeem tokens when the contract is not paused.
     * @param amount_ amount of tokens to redeem
     * @param orderId_ order id that must be unique
     */
    function redeem(uint256 amount_, bytes32 orderId_) external whenNotPaused {
        if (msg.sender == address(0)) revert ZeroAddress();
        if (amount_ == 0) revert ZeroAmount();
        if (isBlacklisted[msg.sender]) revert UserBlacklisted();
        if (isRedeemOrderSubmitted[orderId_]) revert RedeemOrderAlreadySubmitted(orderId_);

        _burn(msg.sender, amount_);
        emit TokensRedeemed(msg.sender, amount_, orderId_);
    }

    /**
     * @dev Emergency pause to transfers, mint and redeem functions.
     */
    function pause() external onlyRole(PAUSER_ROLE) {
        _pause();
        emit PausedContract();
    }

    /**
     * @dev Unpause the transfers, mint and redeem functions.
     */
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
        emit UnpausedContract();
    }

    /**
     * @dev Function that allows the admin to blacklist a user address
     * @param user_ address to be blacklisted
     */
    function blacklist(address user_) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (user_ == address(0)) revert ZeroAddress();
        isBlacklisted[user_] = true;
        emit BlacklistStatusChanged(user_, true);
    }

    /**
     * @dev Function that allows the admin to remove a user from the blacklist
     * @param user_ address to be removed from blacklist
     */
    function removeFromBlacklist(address user_) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (user_ == address(0)) revert ZeroAddress();
        isBlacklisted[user_] = false;
        emit BlacklistStatusChanged(user_, false);
    }

    /**
     * @dev Override token decimals to 6 instead of 18
     */
    function decimals() public pure override returns (uint8) {
        return EURD_DECIMALS;
    }

    /**
     * @dev Transfers a `value` amount of tokens from `from` to `to`, or alternatively mints (or burns) if `from`
     * (or `to`) is the zero address. If any address 'from' or 'to' is blacklisted or the contract is paused, the transaction reverts.
     * This applies for mint, burn, transfer, transferFrom functions.
     * @param from  origin address
     * @param to destination address
     * @param value amount of tokens
     */
    function _update(address from, address to, uint256 value) internal override whenNotPaused {
        if (isBlacklisted[from] || isBlacklisted[to]) revert UserBlacklisted();
        super._update(from, to, value);
    }
}
