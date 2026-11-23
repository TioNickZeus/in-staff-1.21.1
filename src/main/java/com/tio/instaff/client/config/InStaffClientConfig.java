package com.tio.instaff.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side configuration specification for In-Staff.
 * Persists client UI/privacy preferences (e.g. first-time Multiplayer data notice acceptance).
 * This class is strictly CLIENT-side and must never be loaded by dedicated servers.
 */
public final class InStaffClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MULTIPLAYER_NOTICE_ACCEPTED;

    static {
        BUILDER.comment("In-Staff Client Privacy & Consent Settings").push("privacy");
        MULTIPLAYER_NOTICE_ACCEPTED = BUILDER
                .comment("Tracks whether the player has acknowledged the Multiplayer integrity data notice.")
                .define("multiplayerNoticeAccepted", false);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Boolean acceptedOverrideForTesting = null;
    private static volatile boolean sessionAccepted = false;

    private InStaffClientConfig() {
    }

    public static boolean isMultiplayerNoticeAccepted() {
        if (acceptedOverrideForTesting != null) {
            return acceptedOverrideForTesting;
        }
        if (sessionAccepted) {
            return true;
        }
        return SPEC.isLoaded() && MULTIPLAYER_NOTICE_ACCEPTED.get();
    }

    public static void markMultiplayerNoticeAccepted() {
        sessionAccepted = true;
        if (acceptedOverrideForTesting != null) {
            acceptedOverrideForTesting = true;
        }
        if (SPEC.isLoaded()) {
            MULTIPLAYER_NOTICE_ACCEPTED.set(true);
            SPEC.save();
        }
    }

    public static void setAcceptedOverrideForTesting(@Nullable Boolean override) {
        acceptedOverrideForTesting = override;
    }

    public static void resetForTesting() {
        acceptedOverrideForTesting = null;
        sessionAccepted = false;
    }
}
