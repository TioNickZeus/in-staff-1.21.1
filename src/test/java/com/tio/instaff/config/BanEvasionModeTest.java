package com.tio.instaff.config;

import com.tio.instaff.moderation.PunishmentManager;
import com.tio.instaff.moderation.PunishmentRecord;
import com.tio.instaff.moderation.PunishmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TC-09: BanEvasionMode & Offline Evasion Tests")
class BanEvasionModeTest {

    @Test
    @DisplayName("Verify BanEvasionMode enum constants and default configuration")
    void testEnumConstantsAndDefaults() {
        assertEquals(4, BanEvasionMode.values().length);
        assertNotNull(BanEvasionMode.valueOf("STRICT"));
        assertNotNull(BanEvasionMode.valueOf("IP_ONLY"));
        assertNotNull(BanEvasionMode.valueOf("TOKEN_ONLY"));
        assertNotNull(BanEvasionMode.valueOf("OFF"));

        // Default when spec is not loaded is STRICT
        assertEquals(BanEvasionMode.STRICT, InStaffConfig.getBanEvasionMode());
        assertTrue(InStaffConfig.isIpBanEvasionEnabled());
        assertTrue(InStaffConfig.isTokenBanEvasionEnabled());
    }

    @Test
    @DisplayName("Verify mode logic combinations")
    void testModeLogic() {
        // STRICT
        assertTrue(isIpEnabled(BanEvasionMode.STRICT));
        assertTrue(isTokenEnabled(BanEvasionMode.STRICT));

        // IP_ONLY
        assertTrue(isIpEnabled(BanEvasionMode.IP_ONLY));
        assertFalse(isTokenEnabled(BanEvasionMode.IP_ONLY));

        // TOKEN_ONLY
        assertFalse(isIpEnabled(BanEvasionMode.TOKEN_ONLY));
        assertTrue(isTokenEnabled(BanEvasionMode.TOKEN_ONLY));

        // OFF
        assertFalse(isIpEnabled(BanEvasionMode.OFF));
        assertFalse(isTokenEnabled(BanEvasionMode.OFF));
    }

    @Test
    @DisplayName("Verify PunishmentManager active ban lookups by Token and IP")
    void testPunishmentManagerTokenAndIpLookups() {
        PunishmentManager manager = PunishmentManager.getInstance();
        UUID target = UUID.randomUUID();
        String testIp = "10.0.0.42";
        String testToken = UUID.randomUUID().toString();

        PunishmentRecord record = new PunishmentRecord(
                target, "EvasionTester", null, null,
                PunishmentType.BAN, "Testing evasion", -1L, testIp, testToken
        );
        manager.addPunishment(record);

        // Lookup with valid matching criteria
        Optional<PunishmentRecord> byToken = manager.getActiveBanByClientToken(testToken);
        assertTrue(byToken.isPresent());
        assertEquals(target, byToken.get().getTargetUUID());

        Optional<PunishmentRecord> byIp = manager.getActiveBanByIp(testIp);
        assertTrue(byIp.isPresent());
        assertEquals(target, byIp.get().getTargetUUID());

        // Null / blank edge cases
        assertTrue(manager.getActiveBanByClientToken(null).isEmpty());
        assertTrue(manager.getActiveBanByClientToken("").isEmpty());
        assertTrue(manager.getActiveBanByClientToken("   ").isEmpty());
        assertTrue(manager.getActiveBanByIp(null).isEmpty());
        assertTrue(manager.getActiveBanByIp("").isEmpty());
        assertTrue(manager.getActiveBanByIp("   ").isEmpty());

        // Cleanup
        manager.unban(target, null, "Console", "Cleanup");
    }

    private boolean isIpEnabled(BanEvasionMode mode) {
        return mode == BanEvasionMode.STRICT || mode == BanEvasionMode.IP_ONLY;
    }

    private boolean isTokenEnabled(BanEvasionMode mode) {
        return mode == BanEvasionMode.STRICT || mode == BanEvasionMode.TOKEN_ONLY;
    }
}
