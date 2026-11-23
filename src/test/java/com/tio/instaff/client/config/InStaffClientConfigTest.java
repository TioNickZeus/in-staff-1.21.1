package com.tio.instaff.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InStaffClientConfig Multiplayer Consent Tests")
class InStaffClientConfigTest {

    @BeforeEach
    void setUp() {
        InStaffClientConfig.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        InStaffClientConfig.resetForTesting();
    }

    @Test
    @DisplayName("Multiplayer notice defaults to unaccepted when not acknowledged")
    void testDefaultsToUnaccepted() {
        assertFalse(InStaffClientConfig.isMultiplayerNoticeAccepted());
    }

    @Test
    @DisplayName("markMultiplayerNoticeAccepted marks consent as accepted even when SPEC is unloaded")
    void testMarkAcceptedUpdatesStateSafely() {
        assertFalse(InStaffClientConfig.isMultiplayerNoticeAccepted());

        assertDoesNotThrow(InStaffClientConfig::markMultiplayerNoticeAccepted);
        assertTrue(InStaffClientConfig.isMultiplayerNoticeAccepted());
    }

    @Test
    @DisplayName("Test override controls consent status and resetForTesting clears it")
    void testOverrideAndResetBehavior() {
        InStaffClientConfig.setAcceptedOverrideForTesting(Boolean.TRUE);
        assertTrue(InStaffClientConfig.isMultiplayerNoticeAccepted());

        InStaffClientConfig.setAcceptedOverrideForTesting(Boolean.FALSE);
        assertFalse(InStaffClientConfig.isMultiplayerNoticeAccepted());

        InStaffClientConfig.markMultiplayerNoticeAccepted();
        assertTrue(InStaffClientConfig.isMultiplayerNoticeAccepted());

        InStaffClientConfig.resetForTesting();
        assertFalse(InStaffClientConfig.isMultiplayerNoticeAccepted());
    }
}
