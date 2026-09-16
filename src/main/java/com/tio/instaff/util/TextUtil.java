package com.tio.instaff.util;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Text and formatting utility for In-Staff.
 * Provides safe string operations, color code translation, and timestamp formatting.
 */
public final class TextUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private TextUtil() {
    }

    /**
     * Translates alternate color codes ('&') into Minecraft formatting character ('§').
     */
    @NotNull
    public static String colorize(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return COLOR_CODE_PATTERN.matcher(text).replaceAll("§$1");
    }

    /**
     * Strips all Minecraft formatting codes ('§' and '&').
     */
    @NotNull
    public static String stripFormatting(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return ChatFormatting.stripFormatting(colorize(text));
    }

    /**
     * Formats an epoch millisecond timestamp into a human-readable string (yyyy-MM-dd HH:mm:ss).
     */
    @NotNull
    public static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return LocalizationHelper.getRawTranslation("instaff.common.never");
        }
        try {
            return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
        } catch (Exception e) {
            return String.valueOf(epochMillis);
        }
    }

    /**
     * Safely parses a UUID string, returning null if invalid rather than throwing.
     */
    @Nullable
    public static UUID tryParseUUID(@Nullable String uuidString) {
        if (uuidString == null || uuidString.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuidString.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Null-safe string truncation with ellipsis.
     */
    @NotNull
    public static String truncate(@Nullable String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    /**
     * Formats a player name and UUID for logging and audit displays.
     */
    @NotNull
    public static String formatPlayerIdentity(@Nullable String name, @Nullable UUID uuid) {
        String safeName = (name != null && !name.isBlank()) ? name : "Unknown";
        String safeUuid = (uuid != null) ? uuid.toString() : "00000000-0000-0000-0000-000000000000";
        return safeName + " (" + safeUuid + ")";
    }
}
