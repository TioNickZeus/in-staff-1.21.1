package com.tio.instaff.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.tio.instaff.access.MaintenanceManager;
import com.tio.instaff.access.PlaytimeTracker;
import com.tio.instaff.access.WhitelistManager;
import com.tio.instaff.moderation.PunishmentManager;
import com.tio.instaff.moderation.PunishmentRecord;
import com.tio.instaff.moderation.PunishmentType;
import com.tio.instaff.protection.BanItemManager;
import com.tio.instaff.protection.BanItemMode;
import com.tio.instaff.util.DurationParser;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Lote 6: Administrative and Moderation Commands Tests")
class Lote6CommandsTest {

    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher<>();
        StaffCommand.register(dispatcher);
        PunishCommands.register(dispatcher);
        MaintenanceCommand.register(dispatcher);
        WhitelistCommand.register(dispatcher);
        InvseeCommand.register(dispatcher);
        BanItemCommand.register(dispatcher);
        HistoryCommand.register(dispatcher);
        PlaytimeCommand.register(dispatcher);
    }

    @Nested
    @DisplayName("Command Tree Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("All root moderation commands are correctly registered in Brigadier dispatcher")
        void testRootCommandsRegistered() {
            Set<String> expectedRoots = Set.of(
                    "staff", "instaff",
                    "ban", "tempban", "unban",
                    "mute", "tempmute", "unmute",
                    "kick", "freeze",
                    "maintenance",
                    "swhitelist",
                    "invsee", "endersee",
                    "banitem",
                    "history", "checkpunish",
                    "playtime", "seen"
            );

            for (String cmd : expectedRoots) {
                CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(cmd);
                assertNotNull(node, "Command '/" + cmd + "' should be registered in dispatcher.");
            }
        }

        @Test
        @DisplayName("Subcommands for /maintenance are registered")
        void testMaintenanceSubcommands() {
            CommandNode<CommandSourceStack> maintenanceNode = dispatcher.getRoot().getChild("maintenance");
            assertNotNull(maintenanceNode);
            assertNotNull(maintenanceNode.getChild("on"));
            assertNotNull(maintenanceNode.getChild("off"));
            assertNotNull(maintenanceNode.getChild("status"));
            assertNotNull(maintenanceNode.getChild("bypass"));

            CommandNode<CommandSourceStack> bypassNode = maintenanceNode.getChild("bypass");
            assertNotNull(bypassNode.getChild("add"));
            assertNotNull(bypassNode.getChild("remove"));
        }

        @Test
        @DisplayName("Subcommands for /swhitelist are registered")
        void testWhitelistSubcommands() {
            CommandNode<CommandSourceStack> whitelistNode = dispatcher.getRoot().getChild("swhitelist");
            assertNotNull(whitelistNode);
            assertNotNull(whitelistNode.getChild("on"));
            assertNotNull(whitelistNode.getChild("off"));
            assertNotNull(whitelistNode.getChild("add"));
            assertNotNull(whitelistNode.getChild("remove"));
            assertNotNull(whitelistNode.getChild("list"));
            assertNotNull(whitelistNode.getChild("reload"));
        }

        @Test
        @DisplayName("Subcommands for /banitem are registered")
        void testBanItemSubcommands() {
            CommandNode<CommandSourceStack> banItemNode = dispatcher.getRoot().getChild("banitem");
            assertNotNull(banItemNode);
            assertNotNull(banItemNode.getChild("add"));
            assertNotNull(banItemNode.getChild("remove"));
            assertNotNull(banItemNode.getChild("list"));
            assertNotNull(banItemNode.getChild("check"));
        }
    }

    @Nested
    @DisplayName("Command Permission Checks Tests")
    class PermissionTests {

        private CommandSourceStack createSource(int permissionLevel) {
            return new CommandSourceStack(
                    CommandSource.NULL,
                    Vec3.ZERO,
                    Vec2.ZERO,
                    null,
                    permissionLevel,
                    "TestSender",
                    Component.literal("TestSender"),
                    null,
                    null
            );
        }

        @Test
        @DisplayName("Staff root commands enforce permission level 2")
        void testStaffCommandsPermissionRequirement() {
            CommandSourceStack nonOp = createSource(0);
            CommandSourceStack op = createSource(2);

            List<String> opCommands = List.of(
                    "staff", "instaff", "ban", "tempban", "unban",
                    "mute", "tempmute", "unmute", "kick", "freeze",
                    "maintenance", "swhitelist", "invsee", "endersee",
                    "banitem", "history", "checkpunish", "seen"
            );

            for (String cmd : opCommands) {
                CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(cmd);
                assertNotNull(node);
                assertFalse(node.canUse(nonOp), "Command '/" + cmd + "' should NOT be usable by non-OP (level 0).");
                assertTrue(node.canUse(op), "Command '/" + cmd + "' should be usable by OP level 2.");
            }
        }

        @Test
        @DisplayName("/playtime without arguments is accessible to non-OP players")
        void testPlaytimeSelfAccessibleToAll() {
            CommandSourceStack nonOp = createSource(0);

            CommandNode<CommandSourceStack> playtimeNode = dispatcher.getRoot().getChild("playtime");
            assertNotNull(playtimeNode);
            assertTrue(playtimeNode.canUse(nonOp), "/playtime root should be usable by all players.");
        }
    }

    @Nested
    @DisplayName("BanItemMode Parsing and Utility Tests")
    class BanItemModeTests {

        @Test
        @DisplayName("BanItemMode.fromString parses valid and alias values case-insensitively")
        void testModeParsing() {
            assertEquals(BanItemMode.TOTAL, BanItemMode.fromString("TOTAL"));
            assertEquals(BanItemMode.TOTAL, BanItemMode.fromString("total"));
            assertEquals(BanItemMode.TOTAL, BanItemMode.fromString("ALL"));
            assertEquals(BanItemMode.TOTAL, BanItemMode.fromString("all"));
            assertEquals(BanItemMode.TOTAL, BanItemMode.fromString(null));
            assertEquals(BanItemMode.TOTAL, BanItemMode.fromString(""));
            assertEquals(BanItemMode.TOTAL, BanItemMode.fromString("invalid_mode"));

            assertEquals(BanItemMode.NO_USE, BanItemMode.fromString("NO_USE"));
            assertEquals(BanItemMode.NO_USE, BanItemMode.fromString("use"));
            assertEquals(BanItemMode.NO_USE, BanItemMode.fromString("interact"));

            assertEquals(BanItemMode.NO_PLACE, BanItemMode.fromString("NO_PLACE"));
            assertEquals(BanItemMode.NO_PLACE, BanItemMode.fromString("place"));
        }

        @Test
        @DisplayName("BanItemMode restriction rules behave according to specification")
        void testModeBehaviors() {
            assertTrue(BanItemMode.TOTAL.blocksUse());
            assertTrue(BanItemMode.TOTAL.blocksPlacement());
            assertTrue(BanItemMode.TOTAL.blocksPossession());

            assertTrue(BanItemMode.NO_USE.blocksUse());
            assertFalse(BanItemMode.NO_USE.blocksPlacement());
            assertFalse(BanItemMode.NO_USE.blocksPossession());

            assertFalse(BanItemMode.NO_PLACE.blocksUse());
            assertTrue(BanItemMode.NO_PLACE.blocksPlacement());
            assertFalse(BanItemMode.NO_PLACE.blocksPossession());
        }
    }

    @Nested
    @DisplayName("Punishment Lifecycle and Audit Tests")
    class PunishmentLifecycleTests {

        @Test
        @DisplayName("PunishmentRecord properly indicates revoked status upon revocation")
        void testPunishmentRevocation() {
            UUID target = UUID.randomUUID();
            UUID staff = UUID.randomUUID();

            PunishmentRecord record = new PunishmentRecord(
                    target, "TestPlayer", staff, "TestStaff",
                    PunishmentType.BAN, "Griefing", -1L, "127.0.0.1", null
            );

            assertTrue(record.isActive());
            assertFalse(record.isRevoked());
            assertFalse(record.isExpired());

            record.revoke(staff, "TestStaff", "Appealed");

            assertFalse(record.isActive());
            assertTrue(record.isRevoked());
            assertEquals("Appealed", record.getRevokeReason());
        }

        @Test
        @DisplayName("Temporary punishment expiration calculation works accurately")
        void testTempPunishmentExpiration() {
            UUID target = UUID.randomUUID();
            long duration = 3600_000L; // 1 hour

            PunishmentRecord record = new PunishmentRecord(
                    target, "Player1", null, "CONSOLE",
                    PunishmentType.TEMP_MUTE, "Spam", duration, null, null
            );

            assertTrue(record.isActive());
            assertFalse(record.isExpired());
            assertEquals(duration, record.getExpiresAtEpoch() - record.getCreatedAtEpoch());
        }

        @Test
        @DisplayName("Kick records are instantaneous and never active")
        void testKickRecordNeverActive() {
            UUID target = UUID.randomUUID();
            PunishmentRecord kick = new PunishmentRecord(
                    target, "PlayerKicked", null, "CONSOLE",
                    PunishmentType.KICK, "AFK", 0L, null, null
            );

            assertFalse(kick.isActive(), "Kick record must not be an active ongoing punishment.");
        }
    }

    @Nested
    @DisplayName("Language and Localization Consistency Tests")
    class LocalizationCompletenessTests {

        private static final List<String> REQUIRED_KEYS = List.of(
                "instaff.command.staff.help",
                "instaff.command.ban.success",
                "instaff.command.tempban.success",
                "instaff.command.unban.success",
                "instaff.command.unban.not_banned",
                "instaff.command.mute.success",
                "instaff.command.tempmute.success",
                "instaff.command.unmute.success",
                "instaff.command.unmute.not_muted",
                "instaff.command.kick.success",
                "instaff.command.freeze.on",
                "instaff.command.freeze.off",
                "instaff.command.maintenance.enabled",
                "instaff.command.maintenance.disabled",
                "instaff.command.maintenance.status",
                "instaff.command.maintenance.bypass_add",
                "instaff.command.maintenance.bypass_remove",
                "instaff.command.whitelist.enabled",
                "instaff.command.whitelist.disabled",
                "instaff.command.whitelist.add",
                "instaff.command.whitelist.remove",
                "instaff.command.whitelist.reload",
                "instaff.command.whitelist.list",
                "instaff.command.invsee.cannot_self",
                "instaff.command.invsee.opened",
                "instaff.command.endersee.opened",
                "instaff.command.invsee.not_found",
                "instaff.command.banitem.added",
                "instaff.command.banitem.removed",
                "instaff.command.banitem.list",
                "instaff.command.banitem.check",
                "instaff.command.banitem.not_banned",
                "instaff.command.history.header",
                "instaff.command.history.empty",
                "instaff.command.history.entry",
                "instaff.command.playtime.self",
                "instaff.command.playtime.other",
                "instaff.command.seen.result"
        );

        @Test
        @DisplayName("en_us.json contains all required command translation keys")
        void testEnUsLanguageFileCompleteness() {
            Map<String, String> lang = loadLangFile("/assets/instaff/lang/en_us.json");
            for (String key : REQUIRED_KEYS) {
                assertTrue(lang.containsKey(key), "en_us.json is missing translation key: " + key);
                assertFalse(lang.get(key).isBlank(), "en_us.json translation for " + key + " must not be blank");
            }
        }

        @Test
        @DisplayName("pt_br.json contains all required command translation keys")
        void testPtBrLanguageFileCompleteness() {
            Map<String, String> lang = loadLangFile("/assets/instaff/lang/pt_br.json");
            for (String key : REQUIRED_KEYS) {
                assertTrue(lang.containsKey(key), "pt_br.json is missing translation key: " + key);
                assertFalse(lang.get(key).isBlank(), "pt_br.json translation for " + key + " must not be blank");
            }
        }

        private Map<String, String> loadLangFile(String resourcePath) {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            assertNotNull(is, "Could not find resource: " + resourcePath);
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            return new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type);
        }
    }
}
