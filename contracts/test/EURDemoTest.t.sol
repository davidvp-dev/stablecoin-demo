// SPDX-License-Identifier: MIT

pragma solidity 0.8.34;

import {Test} from "forge-std/Test.sol";
import {EURDemo, ZeroAddress, UserBlacklisted} from "../src/EURDemo.sol";
import {IERC20} from "../lib/openzeppelin-contracts/contracts/token/ERC20/IERC20.sol";
import {Pausable} from "../lib/openzeppelin-contracts/contracts/utils/Pausable.sol";

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
    address public receiver = makeAddr("receiver");

    function setUp() public {
        vm.startPrank(admin);
        eurDemo = new EURDemo(minter, pauser, blacklister);
        vm.stopPrank();
    }

    function test_deploy_OK() public view {
        // RF-SC-01: metadata
        assertEq(eurDemo.name(), "Euro Demo");
        assertEq(eurDemo.symbol(), "EURD");
        assertEq(eurDemo.decimals(), 6);

        // RF-SC-10: every account holds exactly its own role
        address[4] memory accounts = [admin, minter, pauser, blacklister];
        bytes32[4] memory roles =
            [eurDemo.DEFAULT_ADMIN_ROLE(), eurDemo.MINTER_ROLE(), eurDemo.PAUSER_ROLE(), eurDemo.BLACKLISTER_ROLE()];
        for (uint256 i = 0; i < accounts.length; i++) {
            for (uint256 j = 0; j < roles.length; j++) {
                assertEq(eurDemo.hasRole(roles[j], accounts[i]), i == j);
            }
            // every role is managed by the admin role
            assertEq(eurDemo.getRoleAdmin(roles[i]), eurDemo.DEFAULT_ADMIN_ROLE());
            // neither the test contract nor the user holds any role
            assertFalse(eurDemo.hasRole(roles[i], address(this)));
            assertFalse(eurDemo.hasRole(roles[i], user));
        }

        // Initial state
        assertEq(eurDemo.totalSupply(), 0);
        assertFalse(eurDemo.paused());
    }

    function test_mintValid_OK() public {
        // RF-SC-02: mint function happy path OK
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(minter);
        bool submittedBefore = eurDemo.isMintOrderSubmitted(orderId);
        uint256 balBefore = IERC20(address(eurDemo)).balanceOf(user);

        eurDemo.mint(user, amount, orderId);

        bool submittedAfter = eurDemo.isMintOrderSubmitted(orderId);
        uint256 balAfter = IERC20(address(eurDemo)).balanceOf(user);

        assertFalse(submittedBefore);
        assertTrue(submittedAfter);
        assertEq(balBefore, 0);
        assertEq(balAfter, amount);

        vm.stopPrank();
    }

    function test_mintInvalidUserZeroAddress_KO() public {
        // RF-SC-02: mint function to zero address KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(minter);
        vm.expectRevert();
        eurDemo.mint(address(0), amount, orderId);

        vm.stopPrank();
    }

    function test_mintInvalidAmountZero_KO() public {
        // RF-SC-02: mint function zero tokens KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(minter);
        vm.expectRevert();
        eurDemo.mint(user, 0, orderId);

        vm.stopPrank();
    }

    function test_mintUserBlacklisted_KO() public {
        // RF-SC-02: mint function to blacklisted user KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(blacklister);
        eurDemo.blacklist(user);
        vm.stopPrank();

        vm.startPrank(minter);
        vm.expectRevert();
        eurDemo.mint(user, amount, orderId);

        vm.stopPrank();
    }

    function test_mintOrderDuplicated_KO() public {
        // RF-SC-04: mint function with orderId duplicated KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(minter);
        bool submittedBefore = eurDemo.isMintOrderSubmitted(orderId);
        uint256 balBefore = IERC20(address(eurDemo)).balanceOf(user);

        eurDemo.mint(user, amount, orderId);

        bool submittedAfter = eurDemo.isMintOrderSubmitted(orderId);
        uint256 balAfter = IERC20(address(eurDemo)).balanceOf(user);

        assertFalse(submittedBefore);
        assertTrue(submittedAfter);
        assertEq(balBefore, 0);
        assertEq(balAfter, amount);

        vm.expectRevert();
        eurDemo.mint(user, amount, orderId);

        vm.stopPrank();
    }

    function test_mintPausedContract_KO() public {
        // RF-SC-08: mint with paused contract KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(pauser);
        eurDemo.pause();
        vm.stopPrank();

        vm.startPrank(minter);
        vm.expectRevert();
        eurDemo.mint(user, amount, orderId);

        vm.stopPrank();
    }

    function test_mintInvalidMinterRole_KO() public {
        // RF-SC-02: mint invalid minter address KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(admin);
        vm.expectRevert();
        eurDemo.mint(user, amount, orderId);

        vm.stopPrank();
    }

    function test_redeem_OK() public {
        // First MINT tokens to the user
        uint256 amountToMint = 10_000;
        bytes32 orderIdMint = keccak256(abi.encodePacked(user, amountToMint, "MINT_1"));

        vm.startPrank(minter);
        bool mintSubmittedBefore = eurDemo.isMintOrderSubmitted(orderIdMint);
        uint256 balBeforeMint = IERC20(address(eurDemo)).balanceOf(user);

        eurDemo.mint(user, amountToMint, orderIdMint);

        bool mintSubmittedAfter = eurDemo.isMintOrderSubmitted(orderIdMint);
        uint256 balAfterMint = IERC20(address(eurDemo)).balanceOf(user);

        assertFalse(mintSubmittedBefore);
        assertTrue(mintSubmittedAfter);
        assertEq(balBeforeMint, 0);
        assertEq(balAfterMint, amountToMint);

        vm.stopPrank();

        // RF-SC-05: redeem function happy path OK
        uint256 amountToRedeem = 5_000;
        bytes32 orderIdRedeem = keccak256(abi.encodePacked(user, amountToRedeem, "REDEEM_1"));

        vm.startPrank(user);
        bool redeemSubmittedBefore = eurDemo.isRedeemOrderSubmitted(user, orderIdRedeem);
        uint256 redeemBalBefore = IERC20(address(eurDemo)).balanceOf(user);

        eurDemo.redeem(amountToRedeem, orderIdRedeem);

        bool redeemSubmittedAfter = eurDemo.isRedeemOrderSubmitted(user, orderIdRedeem);
        uint256 redeemBalAfter = IERC20(address(eurDemo)).balanceOf(user);

        assertFalse(redeemSubmittedBefore);
        assertTrue(redeemSubmittedAfter);
        assertEq(redeemBalBefore, balAfterMint);
        assertEq(redeemBalAfter, balAfterMint - amountToRedeem);

        vm.stopPrank();
    }

    function test_redeemInvalidZeroAmount_KO() public {
        // RF-SC-05: redeem function invalid zero amount KO
        uint256 amountToRedeem = 0;
        bytes32 orderIdRedeem = keccak256(abi.encodePacked(user, amountToRedeem, "REDEEM_1"));

        vm.startPrank(user);
        vm.expectRevert();
        eurDemo.redeem(amountToRedeem, orderIdRedeem);
    }

    function test_redeemBlacklistedUserKO() public {
        // RF-SC-05: redeem function to blacklisted user KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(blacklister);
        eurDemo.blacklist(user);
        vm.stopPrank();

        vm.startPrank(user);
        vm.expectRevert();
        eurDemo.redeem(amount, orderId);

        vm.stopPrank();
    }

    function test_redeemOrderDuplicated_KO() public {
        // First MINT tokens to the user
        uint256 amountToMint = 10_000;
        bytes32 orderIdMint = keccak256(abi.encodePacked(user, amountToMint, "MINT_1"));

        vm.startPrank(minter);
        bool mintSubmittedBefore = eurDemo.isMintOrderSubmitted(orderIdMint);
        uint256 balBeforeMint = IERC20(address(eurDemo)).balanceOf(user);

        eurDemo.mint(user, amountToMint, orderIdMint);

        bool mintSubmittedAfter = eurDemo.isMintOrderSubmitted(orderIdMint);
        uint256 balAfterMint = IERC20(address(eurDemo)).balanceOf(user);

        assertFalse(mintSubmittedBefore);
        assertTrue(mintSubmittedAfter);
        assertEq(balBeforeMint, 0);
        assertEq(balAfterMint, amountToMint);

        vm.stopPrank();

        // RF-SC-06: redeem function with orderId duplicated KO
        uint256 amountToRedeem = 5_000;
        bytes32 orderIdRedeem = keccak256(abi.encodePacked(user, amountToRedeem, "REDEEM_1"));

        vm.startPrank(user);
        bool redeemSubmittedBefore = eurDemo.isRedeemOrderSubmitted(user, orderIdRedeem);
        uint256 redeemBalBefore = IERC20(address(eurDemo)).balanceOf(user);

        eurDemo.redeem(amountToRedeem, orderIdRedeem);

        bool redeemSubmittedAfter = eurDemo.isRedeemOrderSubmitted(user, orderIdRedeem);
        uint256 redeemBalAfter = IERC20(address(eurDemo)).balanceOf(user);

        assertFalse(redeemSubmittedBefore);
        assertTrue(redeemSubmittedAfter);
        assertEq(redeemBalBefore, balAfterMint);
        assertEq(redeemBalAfter, balAfterMint - amountToRedeem);

        vm.expectRevert();
        eurDemo.redeem(amountToRedeem, orderIdRedeem);

        vm.stopPrank();
    }

    function test_redeemPausedContract_KO() public {
        // RF-SC-08: redeem with paused contract KO
        uint256 amount = 10_000;
        bytes32 orderId = keccak256(abi.encodePacked(user, amount, "ORDER_1"));

        vm.startPrank(pauser);
        eurDemo.pause();
        vm.stopPrank();

        vm.startPrank(user);
        vm.expectRevert();
        eurDemo.redeem(amount, orderId);

        vm.stopPrank();
    }

    function test_pauseContract_OK() public {
        vm.startPrank(pauser);
        eurDemo.pause();
        vm.stopPrank();

        uint256 amountToMint = 10_000;
        bytes32 orderIdMint = keccak256(abi.encodePacked(user, amountToMint, "MINT_1"));

        vm.startPrank(minter);
        vm.expectRevert();
        eurDemo.mint(user, amountToMint, orderIdMint);
    }

    function test_unpauseContract_OK() public {
        vm.startPrank(pauser);
        eurDemo.pause();
        vm.stopPrank();

        uint256 amountToMint = 10_000;
        bytes32 orderIdMint = keccak256(abi.encodePacked(user, amountToMint, "MINT_1"));

        vm.startPrank(minter);
        vm.expectRevert();
        eurDemo.mint(user, amountToMint, orderIdMint);
        vm.stopPrank();

        vm.startPrank(pauser);
        eurDemo.unpause();
        vm.stopPrank();

        vm.startPrank(minter);
        bool submittedBefore = eurDemo.isMintOrderSubmitted(orderIdMint);
        uint256 balBefore = IERC20(address(eurDemo)).balanceOf(user);

        eurDemo.mint(user, amountToMint, orderIdMint);

        bool submittedAfter = eurDemo.isMintOrderSubmitted(orderIdMint);
        uint256 balAfter = IERC20(address(eurDemo)).balanceOf(user);

        assertFalse(submittedBefore);
        assertTrue(submittedAfter);
        assertEq(balBefore, 0);
        assertEq(balAfter, amountToMint);
        vm.stopPrank();
    }

    function test_blacklistUser_OK() public {
        vm.startPrank(blacklister);

        bool beforeB = eurDemo.isBlacklisted(user);
        eurDemo.blacklist(user);
        bool afterB = eurDemo.isBlacklisted(user);

        assertFalse(beforeB);
        assertTrue(afterB);

        vm.stopPrank();
    }

    function test_blacklistUserZeroAddress_KO() public {
        vm.startPrank(blacklister);

        vm.expectRevert();
        eurDemo.blacklist(address(0));

        vm.stopPrank();
    }

    function test_blacklistUserNotBlacklisterRole_KO() public {
        vm.startPrank(minter);

        vm.expectRevert();
        eurDemo.blacklist(user);

        vm.stopPrank();
    }

    function test_removeFromBlacklistUser_OK() public {
        vm.startPrank(blacklister);

        bool beforeB = eurDemo.isBlacklisted(user);
        eurDemo.blacklist(user);
        bool afterB = eurDemo.isBlacklisted(user);

        assertFalse(beforeB);
        assertTrue(afterB);

        bool beforeR = eurDemo.isBlacklisted(user);
        eurDemo.removeFromBlacklist(user);
        bool afterR = eurDemo.isBlacklisted(user);

        assertTrue(beforeR);
        assertFalse(afterR);

        vm.stopPrank();
    }

    function test_removeFromBlacklistUserZeroAddress_KO() public {
        vm.startPrank(blacklister);

        vm.expectRevert();
        eurDemo.removeFromBlacklist(address(0));

        vm.stopPrank();
    }

    function test_removeFromBlacklistUserNotBlacklisterRole_KO() public {
        vm.startPrank(minter);

        vm.expectRevert();
        eurDemo.removeFromBlacklist(user);

        vm.stopPrank();
    }

    function test_deployZeroRoleAddress_KO() public {
        // RF-SC-10: every role address must be set
        vm.expectRevert(ZeroAddress.selector);
        new EURDemo(address(0), pauser, blacklister);

        vm.expectRevert(ZeroAddress.selector);
        new EURDemo(minter, address(0), blacklister);

        vm.expectRevert(ZeroAddress.selector);
        new EURDemo(minter, pauser, address(0));
    }

    function test_transfer_OK() public {
        // ERC-20 transfer happy path
        _mintTo(user, 10_000);

        vm.prank(user);
        bool success = eurDemo.transfer(receiver, 4_000);

        assertTrue(success);
        assertEq(eurDemo.balanceOf(user), 6_000);
        assertEq(eurDemo.balanceOf(receiver), 4_000);
        assertEq(eurDemo.totalSupply(), 10_000);
    }

    function test_transferFromBlacklistedUser_KO() public {
        // RF-SC-09: a blacklisted address cannot send
        _mintTo(user, 10_000);

        vm.prank(blacklister);
        eurDemo.blacklist(user);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(UserBlacklisted.selector, user));
        eurDemo.transfer(receiver, 4_000);

        assertEq(eurDemo.balanceOf(user), 10_000);
        assertEq(eurDemo.balanceOf(receiver), 0);
    }

    function test_transferToBlacklistedUser_KO() public {
        // RF-SC-09: a blacklisted address cannot receive
        _mintTo(user, 10_000);

        vm.prank(blacklister);
        eurDemo.blacklist(receiver);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(UserBlacklisted.selector, receiver));
        eurDemo.transfer(receiver, 4_000);

        assertEq(eurDemo.balanceOf(user), 10_000);
        assertEq(eurDemo.balanceOf(receiver), 0);
    }

    function test_transferPausedContract_KO() public {
        // RF-SC-08: transfers are blocked while paused
        _mintTo(user, 10_000);

        vm.prank(pauser);
        eurDemo.pause();

        vm.prank(user);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        eurDemo.transfer(receiver, 4_000);

        assertEq(eurDemo.balanceOf(user), 10_000);
    }

    function _mintTo(address to, uint256 amount) internal {
        vm.prank(minter);
        eurDemo.mint(to, amount, keccak256(abi.encodePacked(to, amount, "MINT_HELPER")));
    }
}
