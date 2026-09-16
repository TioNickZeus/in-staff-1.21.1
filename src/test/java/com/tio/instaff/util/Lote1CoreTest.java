package com.tio.instaff.util;

import com.tio.instaff.config.InStaffConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Lote 1: Core Utilities, Config, and Localization Tests")
class Lote1CoreTest {

    @Nested
    @DisplayName("InStaffConfig Defensive Tests")
    class ConfigTests {
        @Test
        @DisplayName("Config accessors return safe defaults when SPEC is not yet loaded")
        void testConfigDefaultsWhenNotLoaded() {
            // SPEC is not loaded in unit test environment
            assertFalse(InStaffConfig.SPEC.isLoaded(), "Spec should not be loaded in unit test context");

            assertTrue(InStaffConfig.isBroadcastPunishments());
            assertTrue(InStaffConfig.isPreventOfflineBanEvasion());
            assertEquals("1h", InStaffConfig.getDefaultMuteDuration());
            assertEquals(365, InStaffConfig.getMaxTempBanDays());
            assertFalse(InStaffConfig.isMaintenanceEnabled());
            assertNotNull(InStaffConfig.getMaintenanceMotd());
            assertEquals("instaff.maintenance.kick_message", InStaffConfig.getMaintenanceKickMessage());
            assertTrue(InStaffConfig.isQuarantineCorruptedEntities());
            assertTrue(InStaffConfig.isLogQuarantineEvents());
            assertTrue(InStaffConfig.isIntegrityEnabled());
            assertEquals(10, InStaffConfig.getIntegrityTimeoutSeconds());
            assertNotNull(InStaffConfig.getRequiredMods());
            assertNotNull(InStaffConfig.getBlacklistedHashes());
            assertEquals(3, InStaffConfig.getBlacklistedModIds().size());
            assertTrue(InStaffConfig.getBlacklistedModIds().contains("xray"));
        }
    }

    @Nested
    @DisplayName("DurationParser Tests")
    class DurationParserTests {
        @Test
        @DisplayName("Parse valid single unit durations")
        void testSingleUnits() {
            assertEquals(30_000L, DurationParser.parseDurationMillis("30s"));
            assertEquals(60_000L, DurationParser.parseDurationMillis("1m"));
            assertEquals(7_200_000L, DurationParser.parseDurationMillis("2h"));
            assertEquals(86_400_000L, DurationParser.parseDurationMillis("1d"));
            assertEquals(604_800_000L, DurationParser.parseDurationMillis("1w"));
            assertEquals(30L * 86_400_000L, DurationParser.parseDurationMillis("1mo"));
            assertEquals(365L * 86_400_000L, DurationParser.parseDurationMillis("1y"));
        }

        @Test
        @DisplayName("Parse composite durations with mixed spacing and cases")
        void testCompositeDurations() {
            long expected = (1 * 86_400_000L) + (12 * 3_600_000L) + (30 * 60_000L);
            assertEquals(expected, DurationParser.parseDurationMillis("1d12h30m"));
            assertEquals(expected, DurationParser.parseDurationMillis("1D 12H 30M"));
            assertEquals(expected, DurationParser.parseDurationMillis("  1d  12h  30m  "));
        }

        @Test
        @DisplayName("Parse permanent and alias keywords")
        void testPermanentKeywords() {
            assertEquals(DurationParser.PERMANENT, DurationParser.parseDurationMillis("perm"));
            assertEquals(DurationParser.PERMANENT, DurationParser.parseDurationMillis("permanent"));
            assertEquals(DurationParser.PERMANENT, DurationParser.parseDurationMillis("-1"));
            assertEquals(DurationParser.PERMANENT, DurationParser.parseDurationMillis("never"));
        }

        @Test
        @DisplayName("Reject invalid and malformed durations gracefully")
        void testInvalidDurations() {
            assertTrue(DurationParser.tryParseDurationMillis(null).isEmpty());
            assertTrue(DurationParser.tryParseDurationMillis("").isEmpty());
            assertTrue(DurationParser.tryParseDurationMillis("   ").isEmpty());
            assertTrue(DurationParser.tryParseDurationMillis("abc").isEmpty());
            assertTrue(DurationParser.tryParseDurationMillis("123").isEmpty());
            assertTrue(DurationParser.tryParseDurationMillis("-5m").isEmpty());
            assertTrue(DurationParser.tryParseDurationMillis("1d random").isEmpty());
            assertTrue(DurationParser.tryParseDurationMillis("1d12hFOO").isEmpty());

            assertThrows(IllegalArgumentException.class, () -> DurationParser.parseDurationMillis("invalid"));
        }

        @Test
        @DisplayName("Format duration correctly")
        void testFormatDuration() {
            assertEquals("0s", DurationParser.formatDuration(0));
            assertEquals("0s", DurationParser.formatDuration(-100));
            assertEquals("30s", DurationParser.formatDuration(30_000));
            assertEquals("1m 15s", DurationParser.formatDuration(75_000));
            assertEquals("1d 2h 3m 4s", DurationParser.formatDuration(
                    (1 * 86_400_000L) + (2 * 3_600_000L) + (3 * 60_000L) + (4 * 1_000L)
            ));
        }
    }

    @Nested
    @DisplayName("TextUtil Tests")
    class TextUtilTests {
        @Test
        @DisplayName("Color code translation from & to §")
        void testColorize() {
            assertEquals("", TextUtil.colorize(null));
            assertEquals("§aHello §cWorld", TextUtil.colorize("&aHello &cWorld"));
            assertEquals("§lBold §rReset", TextUtil.colorize("&lBold &rReset"));
            assertEquals("Plain text", TextUtil.colorize("Plain text"));
        }

        @Test
        @DisplayName("Strip formatting removes § and & codes")
        void testStripFormatting() {
            assertEquals("", TextUtil.stripFormatting(null));
            assertEquals("Hello World", TextUtil.stripFormatting("&aHello §cWorld"));
            assertEquals("No color", TextUtil.stripFormatting("No color"));
        }

        @Test
        @DisplayName("Safe UUID parsing")
        void testParseUUID() {
            assertNull(TextUtil.tryParseUUID(null));
            assertNull(TextUtil.tryParseUUID(""));
            assertNull(TextUtil.tryParseUUID("not-a-uuid"));

            UUID testUuid = UUID.randomUUID();
            assertEquals(testUuid, TextUtil.tryParseUUID(testUuid.toString()));
        }

        @Test
        @DisplayName("Safe string truncation")
        void testTruncate() {
            assertEquals("", TextUtil.truncate(null, 10));
            assertEquals("Short", TextUtil.truncate("Short", 10));
            assertEquals("1234567...", TextUtil.truncate("1234567890123", 10));
        }

        @Test
        @DisplayName("Format player identity safely")
        void testFormatPlayerIdentity() {
            UUID testUuid = UUID.randomUUID();
            assertEquals("Steve (" + testUuid + ")", TextUtil.formatPlayerIdentity("Steve", testUuid));
            assertEquals("Unknown (00000000-0000-0000-0000-000000000000)", TextUtil.formatPlayerIdentity(null, null));
        }
    }

    @Nested
    @DisplayName("LocalizationHelper Tests")
    class LocalizationTests {
        @Test
        @DisplayName("LocalizationHelper never returns null or throws on null inputs")
        void testDefensiveLocalization() {
            assertNotNull(LocalizationHelper.getPrefix());
            assertNotNull(LocalizationHelper.getMessage(null));
            assertNotNull(LocalizationHelper.getMessage(""));
            assertNotNull(LocalizationHelper.getMessage("any.key", (Object[]) null));
            assertNotNull(LocalizationHelper.getMessage("any.key", (Object) null));
            assertNotNull(LocalizationHelper.getPrefixedMessage("any.key"));
            assertNotNull(LocalizationHelper.getRawTranslation(null));
            assertNotNull(LocalizationHelper.getRawTranslation("any.key", (Object) null));
        }
    }
}
