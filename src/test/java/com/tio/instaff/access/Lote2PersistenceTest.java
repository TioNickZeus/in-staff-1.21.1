package com.tio.instaff.access;

import com.tio.instaff.moderation.PunishmentManager;
import com.tio.instaff.moderation.PunishmentRecord;
import com.tio.instaff.moderation.PunishmentType;
import com.tio.instaff.util.FileStorageUtil;
import com.tio.instaff.util.PlayerResolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Lote 2: Persistence, Moderation & Access Managers Tests")
class Lote2PersistenceTest {

    @TempDir
    static Path sharedTempDir;

    @BeforeAll
    static void setup() {
        FileStorageUtil.setDataDirectoryForTesting(sharedTempDir);
    }

    @AfterAll
    static void tearDown() {
        FileStorageUtil.setDataDirectoryForTesting(null);
    }

    @Nested
    @DisplayName("PlayerResolver Offline UUID Tests")
    class ResolverTests {
        @Test
        @DisplayName("Offline UUID is deterministic and conforms to Minecraft standard")
        void testOfflineUUIDGeneration() {
            String name = "TestPlayer123";
            UUID expected = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            UUID generated = PlayerResolver.createOfflineUUID(name);

            assertEquals(expected, generated);
            assertEquals(generated, PlayerResolver.resolveUUID(null, name));

            // Distinct names produce distinct UUIDs
            assertNotEquals(PlayerResolver.createOfflineUUID("Steve"), PlayerResolver.createOfflineUUID("Alex"));
        }
    }

    @Nested
    @DisplayName("PunishmentRecord Lifecycle Tests")
    class RecordTests {
        @Test
        @DisplayName("Active ban calculation, expiration and revocation")
        void testBanLifecycle() {
            UUID target = UUID.randomUUID();
            PunishmentRecord tempBan = new PunishmentRecord(
                    target, "TargetPlayer", null, null,
                    PunishmentType.TEMP_BAN, "Cheating", 10_000L, "127.0.0.1", "token-xyz"
            );

            assertTrue(tempBan.isActive());
            assertFalse(tempBan.isExpired());
            assertTrue(tempBan.getRemainingMillis() > 0);
            assertEquals("TargetPlayer", tempBan.getTargetName());
            assertEquals("127.0.0.1", tempBan.getIpAddress());
            assertEquals("token-xyz", tempBan.getClientToken());

            // Revoke
            tempBan.revoke(null, "Staffer", "Pardoned");
            assertFalse(tempBan.isActive());
            assertEquals("Pardoned", tempBan.getRevokeReason());
        }

        @Test
        @DisplayName("Permanent ban does not expire")
        void testPermanentBan() {
            UUID target = UUID.randomUUID();
            PunishmentRecord permBan = new PunishmentRecord(
                    target, "PermTarget", null, null,
                    PunishmentType.BAN, "Griefing", -1L, null, null
            );

            assertTrue(permBan.isActive());
            assertFalse(permBan.isExpired());
            assertEquals(-1L, permBan.getExpiresAtEpoch());
        }

        @Test
        @DisplayName("Kick record is recorded but inactive as an ongoing punishment")
        void testKickInactive() {
            UUID target = UUID.randomUUID();
            PunishmentRecord kick = new PunishmentRecord(
                    target, "KickedPlayer", null, null,
                    PunishmentType.KICK, "Spam", 0L, null, null
            );

            assertFalse(kick.isActive(), "Kicks are instantaneous events and should not be active");
        }
    }

    @Nested
    @DisplayName("PunishmentManager Operations & Ban Evasion Tests")
    class ManagerTests {
        private final PunishmentManager manager = PunishmentManager.getInstance();

        @Test
        @DisplayName("Add, retrieve and revoke ban")
        void testBanOperations() {
            UUID target = UUID.randomUUID();
            PunishmentRecord ban = new PunishmentRecord(
                    target, "BadPlayer", null, null,
                    PunishmentType.BAN, "XRay", -1L, "192.168.1.100", "client-token-abc"
            );

            manager.addPunishment(ban);

            Optional<PunishmentRecord> activeBan = manager.getActiveBan(target);
            assertTrue(activeBan.isPresent());
            assertEquals("XRay", activeBan.get().getReason());

            // Offline evasion lookups
            Optional<PunishmentRecord> ipBan = manager.getActiveBanByIp("192.168.1.100");
            assertTrue(ipBan.isPresent());
            assertEquals(target, ipBan.get().getTargetUUID());

            Optional<PunishmentRecord> tokenBan = manager.getActiveBanByClientToken("client-token-abc");
            assertTrue(tokenBan.isPresent());
            assertEquals(target, tokenBan.get().getTargetUUID());

            // Unban
            boolean unbanned = manager.unban(target, null, "Admin", "Mistake");
            assertTrue(unbanned);
            assertTrue(manager.getActiveBan(target).isEmpty());
        }

        @Test
        @DisplayName("Freeze and unfreeze state tracking")
        void testFreezeState() {
            UUID target = UUID.randomUUID();
            assertFalse(manager.isFrozen(target));

            manager.setFrozen(target, "FreezeMe", null, null, true, "Investigation");
            assertTrue(manager.isFrozen(target));

            manager.setFrozen(target, "FreezeMe", null, null, false, "Investigation Complete");
            assertFalse(manager.isFrozen(target));
        }
    }

    @Nested
    @DisplayName("MaintenanceManager Tests")
    class MaintenanceTests {
        private final MaintenanceManager manager = MaintenanceManager.getInstance();

        @Test
        @DisplayName("Maintenance access logic and staff bypass")
        void testMaintenanceAccess() {
            UUID normalPlayer = UUID.randomUUID();
            UUID staffPlayer = UUID.randomUUID();

            manager.setMaintenance(false);
            assertTrue(manager.isAllowed(normalPlayer, false));

            manager.setMaintenance(true);
            assertFalse(manager.isAllowed(normalPlayer, false));
            // OP player allowed
            assertTrue(manager.isAllowed(normalPlayer, true));

            // Whitelisted staff allowed
            manager.addStaffBypass(staffPlayer);
            assertTrue(manager.isAllowed(staffPlayer, false));

            manager.removeStaffBypass(staffPlayer);
            assertFalse(manager.isAllowed(staffPlayer, false));

            manager.setMaintenance(false);
        }
    }

    @Nested
    @DisplayName("WhitelistManager Tests")
    class WhitelistTests {
        private final WhitelistManager manager = WhitelistManager.getInstance();

        @Test
        @DisplayName("Smart whitelist add, remove and check")
        void testWhitelist() {
            UUID target = UUID.randomUUID();

            manager.setEnabled(false);
            assertTrue(manager.isWhitelisted(target));

            manager.setEnabled(true);
            assertFalse(manager.isWhitelisted(target));

            manager.addPlayer(target, "Steve");
            assertTrue(manager.isWhitelisted(target));
            assertEquals("Steve", manager.getAllowedPlayers().get(target));

            manager.removePlayer(target);
            assertFalse(manager.isWhitelisted(target));

            manager.setEnabled(false);
        }
    }

    @Nested
    @DisplayName("PlaytimeTracker Tests")
    class PlaytimeTests {
        private final PlaytimeTracker tracker = PlaytimeTracker.getInstance();

        @Test
        @DisplayName("Session tracking and accumulation")
        void testSessionPlaytime() throws InterruptedException {
            UUID player = UUID.randomUUID();
            assertFalse(tracker.isOnline(player));
            assertEquals(0L, tracker.getTotalPlaytimeMillis(player));

            tracker.onPlayerJoin(player, "ActiveUser");
            assertTrue(tracker.isOnline(player));
            assertTrue(tracker.getFirstSeenEpoch(player) > 0);

            // Small sleep to accumulate session time
            Thread.sleep(50);
            long livePlaytime = tracker.getTotalPlaytimeMillis(player);
            assertTrue(livePlaytime >= 40, "Live playtime should include active session duration");

            tracker.onPlayerLeave(player);
            assertFalse(tracker.isOnline(player));
            assertTrue(tracker.getTotalPlaytimeMillis(player) >= 40);
            assertNotNull(tracker.getFormattedPlaytime(player));
        }
    }

    @Nested
    @DisplayName("FileStorageUtil Atomic Operations Tests")
    class StorageTests {
        @Test
        @DisplayName("Atomic save and load with temporary directory")
        void testAtomicSaveLoad(@TempDir Path tempDir) {
            Path testFile = tempDir.resolve("test_data.json");
            TestPayload payload = new TestPayload("InStaff", 42);

            boolean saved = FileStorageUtil.saveAtomicJson(testFile, payload);
            assertTrue(saved);
            assertTrue(Files.exists(testFile));

            TestPayload loaded = FileStorageUtil.loadJson(testFile, TestPayload.class);
            assertNotNull(loaded);
            assertEquals("InStaff", loaded.name);
            assertEquals(42, loaded.count);
        }

        static class TestPayload {
            String name;
            int count;

            TestPayload(String name, int count) {
                this.name = name;
                this.count = count;
            }
        }
    }
}
