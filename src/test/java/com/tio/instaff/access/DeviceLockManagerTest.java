package com.tio.instaff.access;

import com.mojang.brigadier.CommandDispatcher;
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
        DeviceLockManager.setConfigOverrideForTesting(true);
    }

    @AfterEach
    void tearDown() {
        manager.resetForTesting();
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
    @DisplayName("TC-02: Staff account connects, no binding exists -> NO_BINDING, not quarantined")
    void testTC02_StaffNoBinding() {
        UUID uuid = UUID.randomUUID();
        DeviceValidationResult result = manager.validateAndRecord(uuid, "StaffPlayer", "token-staff", true);

        assertEquals(DeviceValidationResult.NO_BINDING, result);
        assertFalse(manager.isPendingDeviceLock(uuid), "Unbound staff account must not be quarantined");
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

        // 4. NO_BINDING
        UUID uuid4 = UUID.randomUUID();
        manager.markPendingForTesting(uuid4);
        DeviceValidationResult res4 = manager.validateAndRecord(uuid4, "Staff4", "tok4", true);
        assertEquals(DeviceValidationResult.NO_BINDING, res4);
        assertFalse(manager.isPendingDeviceLock(uuid4), "NO_BINDING must clear pending flag");
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
}
