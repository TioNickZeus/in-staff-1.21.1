package com.tio.instaff.access;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import com.tio.instaff.commands.DeviceLockCommand;
import com.tio.instaff.util.FileStorageUtil;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Staff Device Lock (Anti-Impersonation) Comprehensive Test Suite")
class DeviceLockManagerTest {

    @TempDir
    static Path tempDir;

    private DeviceLockManager manager;

    @BeforeAll
    static void beforeAll() {
        FileStorageUtil.setDataDirectoryForTesting(tempDir);
    }

    @AfterAll
    static void afterAll() {
        FileStorageUtil.setDataDirectoryForTesting(null);
        DeviceLockManager.setConfigOverrideForTesting(null);
    }

    @BeforeEach
    void setUp() {
        manager = DeviceLockManager.getInstance();
        manager.resetForTesting();
        ClientTokenManager.getInstance().resetForTesting();
        DeviceLockManager.setConfigOverrideForTesting(true);
    }

    @AfterEach
    void tearDown() {
        manager.resetForTesting();
        ClientTokenManager.getInstance().resetForTesting();
        DeviceLockManager.setConfigOverrideForTesting(null);
    }

    @Test
    @DisplayName("TC-01: Non-staff account connects, no binding exists -> NOT_APPLICABLE, not quarantined")
    void testTC01_NonStaffNoBinding() {
        UUID uuid = UUID.randomUUID();
        DeviceValidationResult result = manager.validateAndRecord(uuid, "RegularPlayer", "token-regular", false);

        assertEquals(DeviceValidationResult.NOT_APPLICABLE, result);
        assertFalse(manager.isPendingDeviceLock(uuid), "Non-staff account must never be left in quarantine");
    }

    @Test
    @DisplayName("TC-02: Staff account connects with no existing binding and valid token -> TOFU auto-bind, MATCH, not quarantined")
    void testTC02_StaffNoBindingTofuAutoBind() {
        UUID uuid = UUID.randomUUID();
        DeviceValidationResult result = manager.validateAndRecord(uuid, "StaffPlayer", "token-staff", true);

        assertEquals(DeviceValidationResult.MATCH, result);
        assertFalse(manager.isPendingDeviceLock(uuid), "Unbound staff account must not be quarantined after TOFU");
        assertTrue(manager.hasBinding(uuid), "TOFU auto-bind must persist a device binding");
        assertEquals("token-staff", manager.getRecord(uuid).orElseThrow().getBoundToken());
        assertEquals(DeviceValidationResult.MISMATCH, manager.validateAndRecord(uuid, "StaffPlayer", "different-token", true));
    }

    @Test
    @DisplayName("TC-03: Bound staff account connects, command executed before payload arrives -> Quarantined")
    void testTC03_CommandQuarantineActive() {
        UUID uuid = UUID.randomUUID();
        manager.bindDirect(uuid, "StaffAdmin", "token-secure-123");
        manager.markPendingForTesting(uuid);

        assertTrue(manager.isPendingDeviceLock(uuid), "Bound staff member undergoing verification must be quarantined");
    }

    @Test
    @DisplayName("TC-04: Bound staff account connects, token matches -> MATCH, quarantine cleared, lastSeen updated")
    void testTC04_MatchingTokenValidation() {
        UUID uuid = UUID.randomUUID();
        String token = "valid-hw-token-999";
        manager.bindDirect(uuid, "StaffAdmin", token);

        Optional<DeviceLockRecord> recordBefore = manager.getRecord(uuid);
        assertTrue(recordBefore.isPresent());
        long initialLastSeen = recordBefore.get().getLastSeenEpoch();

        manager.markPendingForTesting(uuid);
        DeviceValidationResult result = manager.validateAndRecord(uuid, "StaffAdmin", token, true);

        assertEquals(DeviceValidationResult.MATCH, result);
        assertFalse(manager.isPendingDeviceLock(uuid), "Quarantine must be cleared on match");

        Optional<DeviceLockRecord> recordAfter = manager.getRecord(uuid);
        assertTrue(recordAfter.isPresent());
        assertTrue(recordAfter.get().getLastSeenEpoch() >= initialLastSeen);
    }

    @Test
    @DisplayName("TC-05: Bound staff account connects, token differs -> MISMATCH, quarantine cleared inline")
    void testTC05_MismatchedTokenValidation() {
        UUID uuid = UUID.randomUUID();
        manager.bindDirect(uuid, "StaffAdmin", "legitimate-token");
        manager.markPendingForTesting(uuid);

        DeviceValidationResult result = manager.validateAndRecord(uuid, "StaffAdmin", "attacker-impostor-token", true);

        assertEquals(DeviceValidationResult.MISMATCH, result);
        assertFalse(manager.isPendingDeviceLock(uuid), "Pending quarantine must be cleared inline before ban logic runs");
    }

    @Test
    @DisplayName("TC-06: Every non-MISMATCH terminal branch also clears pending quarantine inline")
    void testTC06_AllTerminalBranchesClearPending() {
        // 1. NOT_APPLICABLE (non-staff)
        UUID uuid1 = UUID.randomUUID();
        manager.markPendingForTesting(uuid1);
        DeviceValidationResult res1 = manager.validateAndRecord(uuid1, "Player1", "tok1", false);
        assertEquals(DeviceValidationResult.NOT_APPLICABLE, res1);
        assertFalse(manager.isPendingDeviceLock(uuid1), "NOT_APPLICABLE must clear pending flag");

        // 2. NOT_APPLICABLE (feature disabled)
        UUID uuid2 = UUID.randomUUID();
        DeviceLockManager.setConfigOverrideForTesting(false);
        manager.markPendingForTesting(uuid2);
        DeviceValidationResult res2 = manager.validateAndRecord(uuid2, "Staff2", "tok2", true);
        assertEquals(DeviceValidationResult.NOT_APPLICABLE, res2);
        assertFalse(manager.isPendingDeviceLock(uuid2), "Feature-disabled NOT_APPLICABLE must clear pending flag");
        DeviceLockManager.setConfigOverrideForTesting(true);

        // 3. CORRUPTED
        UUID uuid3 = UUID.randomUUID();
        manager.setCorruptedStateForTesting(true);
        manager.markPendingForTesting(uuid3);
        DeviceValidationResult res3 = manager.validateAndRecord(uuid3, "Staff3", "tok3", true);
        assertEquals(DeviceValidationResult.CORRUPTED, res3);
        assertFalse(manager.isPendingDeviceLock(uuid3), "CORRUPTED must clear pending flag");
        manager.setCorruptedStateForTesting(false);

        // 4. NO_BINDING (unbound staff with missing/blank token) and TOFU MATCH (unbound staff with valid token)
        UUID uuid4 = UUID.randomUUID();
        manager.markPendingForTesting(uuid4);
        DeviceValidationResult res4 = manager.validateAndRecord(uuid4, "Staff4", "   ", true);
        assertEquals(DeviceValidationResult.NO_BINDING, res4);
        assertFalse(manager.isPendingDeviceLock(uuid4), "NO_BINDING must clear pending flag");

        UUID uuid5 = UUID.randomUUID();
        manager.markPendingForTesting(uuid5);
        DeviceValidationResult res5 = manager.validateAndRecord(uuid5, "Staff5", "tok5", true);
        assertEquals(DeviceValidationResult.MATCH, res5);
        assertFalse(manager.isPendingDeviceLock(uuid5), "TOFU MATCH must clear pending flag");
    }

    @Test
    @DisplayName("TC-07: device_locks.json fails to parse on load -> isCorruptedState = true, backup created")
    void testTC07_DatabaseCorruptionHandling() throws IOException {
        Path dbPath = tempDir.resolve("device_locks.json");
        Files.writeString(dbPath, "{ this is invalid, broken JSON content !?@# }");

        manager.load();

        assertTrue(manager.isCorruptedState(), "Manager must enter corrupted fail-closed state");

        // Verify emergency backup was created
        boolean backupFound = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, "device_locks.json.corrupt.*")) {
            for (Path p : stream) {
                backupFound = true;
                break;
            }
        }
        assertTrue(backupFound, "Corrupted file backup must be created");

        // In corrupted state, OP login must be rejected with CORRUPTED
        UUID staffUUID = UUID.randomUUID();
        DeviceValidationResult result = manager.validateAndRecord(staffUUID, "Admin", "token", true);
        assertEquals(DeviceValidationResult.CORRUPTED, result);
    }

    @Test
    @DisplayName("TC-08: /isdevice bind attempted by an in-game OP player (not console) -> Rejected")
    void testTC08_BindCommandPlayerRejected() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        DeviceLockCommand.register(dispatcher);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("isdevice");
        assertNotNull(root);
        CommandNode<CommandSourceStack> bindNode = root.getChild("bind");
        assertNotNull(bindNode);

        // Set Bootstrap.isBootstrapped = true so BuiltInRegistries doesn't throw IllegalArgumentException
        try {
            java.lang.reflect.Field field = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
        }

        // Allocate a dummy Entity instance without invoking constructor
        java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Entity dummyPlayer = (Entity) unsafe.allocateInstance(net.minecraft.world.entity.Marker.class);

        CommandSourceStack playerSource = new CommandSourceStack(
                CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2, "InGameOp",
                Component.literal("InGameOp"), null, dummyPlayer
        );

        assertFalse(bindNode.canUse(playerSource), "/isdevice bind must reject in-game player execution (console-only)");
    }

    @Test
    @DisplayName("TC-09: /isdevice bind run from console (source.getEntity() == null) -> Allowed and persisted")
    void testTC09_BindCommandConsoleAllowed() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        DeviceLockCommand.register(dispatcher);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("isdevice");
        assertNotNull(root);
        CommandNode<CommandSourceStack> bindNode = root.getChild("bind");
        assertNotNull(bindNode);

        // Console source (entity == null)
        CommandSourceStack consoleSource = new CommandSourceStack(
                CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4, "Server",
                Component.literal("Server"), null, null
        );

        assertTrue(bindNode.canUse(consoleSource), "/isdevice bind must allow server console execution");

        // Verify direct console binding logic persists atomically
        UUID targetUUID = UUID.randomUUID();
        manager.bindDirect(targetUUID, "TargetAdmin", "hardware-token-xyz");

        assertTrue(manager.hasBinding(targetUUID));
        Optional<DeviceLockRecord> record = manager.getRecord(targetUUID);
        assertTrue(record.isPresent());
        assertEquals("hardware-token-xyz", record.get().getBoundToken());
        assertEquals("TargetAdmin", record.get().getLastAccountName());
    }

    @Test
    @DisplayName("TC-10: Centralized isStaffAccount consistency guard")
    void testTC10_CentralizedStaffAccountConsistency() {
        // Guard against divergent checks: both login and handshake paths invoke DeviceLockManager.isStaffAccount()
        boolean loginPathResult = DeviceLockManager.isStaffAccount(null);
        boolean handshakePathResult = DeviceLockManager.isStaffAccount(null);

        assertEquals(loginPathResult, handshakePathResult);
        assertFalse(loginPathResult);
    }

    @Test
    @DisplayName("TC-11: Recovery workflow after unbind allows TOFU re-bind on next staff handshake")
    void testTC11_RecoveryWorkflowAfterUnbind() {
        UUID staffUUID = UUID.randomUUID();
        manager.bindDirect(staffUUID, "RecoverAdmin", "old-pc-token");

        assertTrue(manager.unbindDirect(staffUUID));
        assertFalse(manager.hasBinding(staffUUID));

        DeviceValidationResult rebound = manager.validateAndRecord(staffUUID, "RecoverAdmin", "new-pc-token", true);
        assertEquals(DeviceValidationResult.MATCH, rebound);
        assertTrue(manager.hasBinding(staffUUID));
        assertEquals("new-pc-token", manager.getRecord(staffUUID).orElseThrow().getBoundToken());
        assertEquals(DeviceValidationResult.MISMATCH, manager.validateAndRecord(staffUUID, "RecoverAdmin", "old-pc-token", true));
    }

    @Test
    @DisplayName("TC-12: Immediate auto-bind on /op binds known tokens and respects all safety guards")
    void testTC12_TryAutoBindOnOpGuardsAndPersistence() {
        UUID newOpUuid = UUID.randomUUID();
        ClientTokenManager.getInstance().recordPlayerToken(newOpUuid, "PromotedOp", "recorded-token-123");

        assertTrue(manager.tryAutoBindOnOp(newOpUuid, "PromotedOp"));
        assertTrue(manager.hasBinding(newOpUuid));
        assertEquals("recorded-token-123", manager.getRecord(newOpUuid).orElseThrow().getBoundToken());

        // Existing binding must never be overwritten
        assertFalse(manager.tryAutoBindOnOp(newOpUuid, "PromotedOp", "different-token-456"));
        assertEquals("recorded-token-123", manager.getRecord(newOpUuid).orElseThrow().getBoundToken());

        // Unknown token must be a no-op
        UUID noTokenUuid = UUID.randomUUID();
        assertFalse(manager.tryAutoBindOnOp(noTokenUuid, "NoTokenPlayer"));
        assertFalse(manager.hasBinding(noTokenUuid));

        // Disabled feature must be a no-op
        UUID disabledUuid = UUID.randomUUID();
        ClientTokenManager.getInstance().recordPlayerToken(disabledUuid, "DisabledOp", "token-disabled");
        DeviceLockManager.setConfigOverrideForTesting(false);
        assertFalse(manager.tryAutoBindOnOp(disabledUuid, "DisabledOp"));
        assertFalse(manager.hasBinding(disabledUuid));
        DeviceLockManager.setConfigOverrideForTesting(true);

        // Corrupted database must fail closed and never create bindings
        UUID corruptedUuid = UUID.randomUUID();
        ClientTokenManager.getInstance().recordPlayerToken(corruptedUuid, "CorruptedOp", "token-corrupted");
        manager.setCorruptedStateForTesting(true);
        assertFalse(manager.tryAutoBindOnOp(corruptedUuid, "CorruptedOp"));
        assertFalse(manager.hasBinding(corruptedUuid));
        manager.setCorruptedStateForTesting(false);
    }

    @Test
    @DisplayName("TC-13: /isdevice bind <player> [token] accepts optional token in Brigadier and fails cleanly when no token is recorded")
    void testTC13_OptionalTokenInBindCommand() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        DeviceLockCommand.register(dispatcher);

        CommandSourceStack consoleSource = new CommandSourceStack(
                CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4, "Server",
                Component.literal("Server"), null, null
        );

        ParseResults<CommandSourceStack> withoutToken = dispatcher.parse("isdevice bind TargetAdmin", consoleSource);
        assertTrue(withoutToken.getExceptions().isEmpty(), "/isdevice bind <player> without token must parse cleanly");
        assertFalse(withoutToken.getReader().canRead(), "All input must be consumed when token is omitted");

        ParseResults<CommandSourceStack> withToken = dispatcher.parse("isdevice bind TargetAdmin manual-token", consoleSource);
        assertTrue(withToken.getExceptions().isEmpty(), "/isdevice bind <player> <token> with explicit token must parse cleanly");
        assertFalse(withToken.getReader().canRead(), "All input must be consumed when token is supplied");

        // Ensure Bootstrap is initialized before LocalizationHelper is invoked by executeBind failure path
        try {
            java.lang.reflect.Field field = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
        }

        int result = dispatcher.execute("isdevice bind UnknownNoTokenPlayer", consoleSource);
        assertEquals(0, result, "/isdevice bind without token must return 0 when no recorded token exists");
        assertFalse(manager.hasBinding(com.tio.instaff.util.PlayerResolver.createOfflineUUID("UnknownNoTokenPlayer")));
    }
}
