// SPDX-License-Identifier: MIT

pragma solidity 0.8.34;

import {Test} from "forge-std/Test.sol";
import {EURDemo} from "../src/EURDemo.sol";
import {DeployEURDemo} from "../script/DeployEURDemo.s.sol";

/**
 * @dev Tests for the deploy script (RF-SC-10, RF-SC-14).
 *
 *      Note on environment variables: vm.setEnv changes the variables of the whole forge process, and forge runs test
 *      functions in parallel. So each test that uses vm.setEnv uses its own network suffix, and no two tests set the
 *      same variable to different values.
 */
contract DeployEURDemoTest is Test {
    DeployEURDemo internal script;

    // Test accounts. makeAddrAndKey derives them from a label, so no real key is used.
    address internal admin;
    uint256 internal adminKey;
    address internal minter = makeAddr("minter");
    address internal pauser = makeAddr("pauser");
    address internal blacklister = makeAddr("blacklister");

    function setUp() public {
        script = new DeployEURDemo();
        (admin, adminKey) = makeAddrAndKey("admin");
    }

    // ═══════════════════════════════ run(): reads .env ═══════════════════════════════

    function test_runAnvil_OK() public {
        // 1. Run on Anvil (chain id 31337) with valid variables.
        vm.chainId(31337);
        vm.setEnv("PRIVATE_KEY_ANVIL", vm.toString(bytes32(adminKey)));
        vm.setEnv("MINTER_ADDRESS_ANVIL", vm.toString(minter));
        vm.setEnv("PAUSER_ADDRESS_ANVIL", vm.toString(pauser));
        vm.setEnv("BLACKLISTER_ADDRESS_ANVIL", vm.toString(blacklister));

        // 2. loadConfig reads each variable into the right field.
        DeployEURDemo.DeployConfig memory config = script.loadConfig();
        assertEq(config.deployerPrivateKey, adminKey);
        assertEq(config.minter, minter);
        assertEq(config.pauser, pauser);
        assertEq(config.blacklister, blacklister);

        // 3. run() deploys, and each account gets exactly its role (RF-SC-10).
        EURDemo eurDemo = script.run();
        _assertExactRoles(eurDemo);
    }

    function test_runSepoliaZeroRoleAddress_KO() public {
        // 1. Run on Sepolia, with the minter left as the zero address.
        //    This also covers the Sepolia branch of the network detection.
        vm.chainId(11155111);
        vm.setEnv("PRIVATE_KEY_SEPOLIA", vm.toString(bytes32(adminKey)));
        vm.setEnv("MINTER_ADDRESS_SEPOLIA", vm.toString(address(0)));
        vm.setEnv("PAUSER_ADDRESS_SEPOLIA", vm.toString(pauser));
        vm.setEnv("BLACKLISTER_ADDRESS_SEPOLIA", vm.toString(blacklister));

        // 2. The script stops before deploying, naming the variable that is wrong.
        vm.expectRevert(abi.encodeWithSelector(DeployEURDemo.ZeroRoleAddress.selector, "MINTER_ADDRESS_SEPOLIA"));
        script.run();
    }

    function test_runUnsupportedChain_KO() public {
        // Any chain other than Anvil or Sepolia (here Ethereum mainnet) is rejected before reading .env.
        vm.chainId(1);
        vm.expectRevert(abi.encodeWithSelector(DeployEURDemo.UnsupportedChain.selector, 1));
        script.run();
    }

    // ═══════════════════════════════ deploy(): role check ═══════════════════════════════

    function test_deploy_OK() public {
        EURDemo eurDemo = script.deploy(_config());
        _assertExactRoles(eurDemo);
    }

    /**
     * @dev The four tests below cover checkRoles(), the safety check at the end of the script. A correct EURDemo always
     *      grants the roles, so to reach each revert the test fakes a broken contract: it deploys a normal one, then
     *      vm.mockCall makes one hasRole() call return false.
     */
    function test_checkRolesAdminMissing_KO() public {
        _expectRoleMissing(bytes32(0), admin, "DEFAULT_ADMIN_ROLE"); // DEFAULT_ADMIN_ROLE is 0x00
    }

    function test_checkRolesMinterMissing_KO() public {
        _expectRoleMissing(keccak256("MINTER_ROLE"), minter, "MINTER_ROLE");
    }

    function test_checkRolesPauserMissing_KO() public {
        _expectRoleMissing(keccak256("PAUSER_ROLE"), pauser, "PAUSER_ROLE");
    }

    function test_checkRolesBlacklisterMissing_KO() public {
        _expectRoleMissing(keccak256("BLACKLISTER_ROLE"), blacklister, "BLACKLISTER_ROLE");
    }

    // ═══════════════════════════════ Helpers ═══════════════════════════════

    function _config() internal view returns (DeployEURDemo.DeployConfig memory) {
        return DeployEURDemo.DeployConfig({
            deployerPrivateKey: adminKey, minter: minter, pauser: pauser, blacklister: blacklister
        });
    }

    /// @dev Deploys a normal EURDemo, makes hasRole(role, account) return false, and expects checkRoles to revert.
    function _expectRoleMissing(bytes32 role, address account, string memory roleName) internal {
        // 1. Deploy a correct contract with the expected roles.
        vm.prank(admin);
        EURDemo eurDemo = new EURDemo(minter, pauser, blacklister);

        // 2. Fake a broken contract: this one hasRole call now answers false.
        vm.mockCall(address(eurDemo), abi.encodeCall(eurDemo.hasRole, (role, account)), abi.encode(false));

        // 3. The check must refuse it, naming the missing role.
        vm.expectRevert(abi.encodeWithSelector(DeployEURDemo.RoleNotGranted.selector, roleName));
        script.checkRoles(eurDemo, admin, _config());
    }

    /// @dev RF-SC-10: each account holds exactly its own role and none of the others.
    function _assertExactRoles(EURDemo eurDemo) internal view {
        address[4] memory accounts = [admin, minter, pauser, blacklister];
        bytes32[4] memory roles =
            [eurDemo.DEFAULT_ADMIN_ROLE(), eurDemo.MINTER_ROLE(), eurDemo.PAUSER_ROLE(), eurDemo.BLACKLISTER_ROLE()];
        for (uint256 i = 0; i < accounts.length; i++) {
            for (uint256 j = 0; j < roles.length; j++) {
                assertEq(eurDemo.hasRole(roles[j], accounts[i]), i == j);
            }
        }
    }
}
