package com.tio.instaff.config;

/**
 * Modes of offline ban evasion detection.
 */
public enum BanEvasionMode {
    /** Checks both IP and Client Token against active bans (recommended for offline servers). */
    STRICT,
    /** Checks only IP address against active bans (ignores Client Token). */
    IP_ONLY,
    /** Checks only Client Token against active bans (useful if players share IP via CGNAT or dorms). */
    TOKEN_ONLY,
    /** Disables all secondary offline evasion checks (only Minecraft UUID is checked). */
    OFF
}
