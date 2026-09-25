package com.tio.instaff.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;

/**
 * Server-authoritative configuration specification for In-Staff.
 * Strictly adheres to defensive programming: accessors verify SPEC.isLoaded()
 * before retrieving values to avoid bootstrapping or shutdown race conditions.
 */
public final class InStaffConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Moderation
    public static final ModConfigSpec.BooleanValue BROADCAST_PUNISHMENTS;
    public static final ModConfigSpec.EnumValue<BanEvasionMode> BAN_EVASION_MODE;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_MUTE_DURATION;
    public static final ModConfigSpec.IntValue MAX_TEMP_BAN_DAYS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_MUTE_COMMANDS;

    // Maintenance
    public static final ModConfigSpec.BooleanValue MAINTENANCE_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> MAINTENANCE_MOTD;
    public static final ModConfigSpec.ConfigValue<String> MAINTENANCE_KICK_MESSAGE;

    // Protection
    public static final ModConfigSpec.BooleanValue QUARANTINE_CORRUPTED_ENTITIES;
    public static final ModConfigSpec.BooleanValue LOG_QUARANTINE_EVENTS;

    // Integrity
    public static final ModConfigSpec.BooleanValue INTEGRITY_ENABLED;
    public static final ModConfigSpec.IntValue INTEGRITY_TIMEOUT_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REQUIRED_MODS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_HASHES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_MOD_IDS;

    static {
        BUILDER.comment("In-Staff Moderation Settings").push("moderation");
        BROADCAST_PUNISHMENTS = BUILDER
                .comment("Whether punishments (bans, mutes, kicks) should be broadcasted to all online players.")
                .define("broadcastPunishments", true);

        BAN_EVASION_MODE = BUILDER
                .comment("Offline ban evasion prevention mode:",
                        "STRICT     - Enforces both IP and Client Token checks against active bans (recommended for offline servers).",
                        "IP_ONLY    - Enforces only IP address check (useful if multiple players share the same physical PC).",
                        "TOKEN_ONLY - Enforces only Client Token check (useful if multiple players share an IP via CGNAT or dorms).",
                        "OFF        - Disables all secondary offline evasion checks (only Minecraft UUID is checked).")
                .defineEnum("banEvasionMode", BanEvasionMode.STRICT);

        DEFAULT_MUTE_DURATION = BUILDER
                .comment("Default duration when mute duration is omitted (e.g., '1h', '30m').")
                .define("defaultMuteDuration", "1h");

        MAX_TEMP_BAN_DAYS = BUILDER
                .comment("Maximum duration in days allowed for temporary bans.")
                .defineInRange("maxTempBanDays", 365, 1, 36500);

        BLOCKED_MUTE_COMMANDS = BUILDER
                .comment("List of command names blocked for muted players (e.g., 'msg', 'tell', 'me').")
                .defineList("blockedMuteCommands", List.of("msg", "tell", "w", "r", "reply", "me", "g", "global"), o -> o instanceof String);
        BUILDER.pop();

        BUILDER.comment("In-Staff Maintenance Settings").push("maintenance");
        MAINTENANCE_ENABLED = BUILDER
                .comment("Controls whether maintenance mode is enabled, restricting non-staff logins.")
                .define("enabled", false);

        MAINTENANCE_MOTD = BUILDER
                .comment("Server list MOTD displayed when maintenance is active.")
                .define("motd", "§cServer under Maintenance §8- §eStaff Only");

        MAINTENANCE_KICK_MESSAGE = BUILDER
                .comment("Translation key or text shown when rejecting unauthorized players during maintenance.")
                .define("kickMessage", "instaff.maintenance.kick_message");
        BUILDER.pop();

        BUILDER.comment("In-Staff Protection and Quarantine Settings").push("protection");
        QUARANTINE_CORRUPTED_ENTITIES = BUILDER
                .comment("Whether to isolate and strip ticking entities/block entities that cause unexpected crashes instead of halting the server.")
                .define("quarantineCorruptedEntities", true);

        LOG_QUARANTINE_EVENTS = BUILDER
                .comment("Log quarantined entities and coordinates to instaff/quarantine.log.")
                .define("logQuarantineEvents", true);
        BUILDER.pop();

        BUILDER.comment("In-Staff Client Integrity & Anti-Cheat Handshake").push("integrity");
        INTEGRITY_ENABLED = BUILDER
                .comment("Enable or disable the client mod/resourcepack integrity handshake on join.")
                .define("enabled", true);

        INTEGRITY_TIMEOUT_SECONDS = BUILDER
                .comment("Timeout in seconds before kicking a client that failed to respond to the integrity handshake.")
                .defineInRange("timeoutSeconds", 10, 3, 120);

        REQUIRED_MODS = BUILDER
                .comment("List of required mod IDs that must be present on connecting clients.")
                .defineList("requiredMods", Collections.emptyList(), o -> o instanceof String);

        BLACKLISTED_HASHES = BUILDER
                .comment("List of SHA-256 hashes of forbidden mods/resourcepacks.")
                .defineList("blacklistedHashes", Collections.emptyList(), o -> o instanceof String);

        BLACKLISTED_MOD_IDS = BUILDER
                .comment("List of forbidden mod IDs (e.g., cheat mods like xray, baritone, freecam).")
                .defineList("blacklistedModIds", List.of("xray", "freecam", "baritone"), o -> o instanceof String);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private InStaffConfig() {
    }

    // Defensive helper accessors
    public static boolean isBroadcastPunishments() {
        return SPEC.isLoaded() ? BROADCAST_PUNISHMENTS.get() : true;
    }

    public static BanEvasionMode getBanEvasionMode() {
        return SPEC.isLoaded() ? BAN_EVASION_MODE.get() : BanEvasionMode.STRICT;
    }

    public static boolean isIpBanEvasionEnabled() {
        BanEvasionMode mode = getBanEvasionMode();
        return mode == BanEvasionMode.STRICT || mode == BanEvasionMode.IP_ONLY;
    }

    public static boolean isTokenBanEvasionEnabled() {
        BanEvasionMode mode = getBanEvasionMode();
        return mode == BanEvasionMode.STRICT || mode == BanEvasionMode.TOKEN_ONLY;
    }

    public static String getDefaultMuteDuration() {
        return SPEC.isLoaded() ? DEFAULT_MUTE_DURATION.get() : "1h";
    }

    public static int getMaxTempBanDays() {
        return SPEC.isLoaded() ? MAX_TEMP_BAN_DAYS.get() : 365;
    }

    public static List<? extends String> getBlockedMuteCommands() {
        return SPEC.isLoaded() ? BLOCKED_MUTE_COMMANDS.get() : List.of();
    }

    public static boolean isMaintenanceEnabled() {
        return SPEC.isLoaded() ? MAINTENANCE_ENABLED.get() : false;
    }

    public static String getMaintenanceMotd() {
        return SPEC.isLoaded() ? MAINTENANCE_MOTD.get() : "§cServer under Maintenance §8- §eStaff Only";
    }

    public static String getMaintenanceKickMessage() {
        return SPEC.isLoaded() ? MAINTENANCE_KICK_MESSAGE.get() : "instaff.maintenance.kick_message";
    }

    public static boolean isQuarantineCorruptedEntities() {
        return SPEC.isLoaded() ? QUARANTINE_CORRUPTED_ENTITIES.get() : true;
    }

    public static boolean isLogQuarantineEvents() {
        return SPEC.isLoaded() ? LOG_QUARANTINE_EVENTS.get() : true;
    }

    public static boolean isIntegrityEnabled() {
        return SPEC.isLoaded() ? INTEGRITY_ENABLED.get() : true;
    }

    public static int getIntegrityTimeoutSeconds() {
        return SPEC.isLoaded() ? INTEGRITY_TIMEOUT_SECONDS.get() : 10;
    }

    public static List<? extends String> getRequiredMods() {
        return SPEC.isLoaded() ? REQUIRED_MODS.get() : Collections.emptyList();
    }

    public static List<? extends String> getBlacklistedHashes() {
        return SPEC.isLoaded() ? BLACKLISTED_HASHES.get() : Collections.emptyList();
    }

    public static List<? extends String> getBlacklistedModIds() {
        return SPEC.isLoaded() ? BLACKLISTED_MOD_IDS.get() : List.of("xray", "freecam", "baritone");
    }
}