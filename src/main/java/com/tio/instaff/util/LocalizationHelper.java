package com.tio.instaff.util;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Localization helper for In-Staff.
 * Provides translated Components and raw Strings with graceful fallbacks.
 * Guaranteed to never throw NullPointerException under any missing translation or null argument.
 */
public final class LocalizationHelper {

    public static final String PREFIX_KEY = "instaff.prefix";
    public static final String DEFAULT_PREFIX = "§8[§bIn-Staff§8] §r";

    private LocalizationHelper() {
    }

    /**
     * Returns the styled mod prefix as a MutableComponent.
     */
    @NotNull
    public static MutableComponent getPrefix() {
        return Component.translatableWithFallback(PREFIX_KEY, DEFAULT_PREFIX);
    }

    /**
     * Creates a translatable component with the key as graceful fallback.
     * Sanitizes arguments to ensure null values do not cause NPE.
     *
     * @param key  Translation key (e.g. "instaff.maintenance.kick_message")
     * @param args Formatting arguments
     * @return Safe MutableComponent
     */
    @NotNull
    public static MutableComponent getMessage(@Nullable String key, @Nullable Object... args) {
        if (key == null || key.isBlank()) {
            return Component.literal("instaff.error.missing_key");
        }

        Object[] sanitizedArgs = sanitizeArgs(args);
        return Component.translatableWithFallback(key, key, sanitizedArgs);
    }

    /**
     * Creates a translatable message prefixed with the In-Staff mod prefix.
     *
     * @param key  Translation key
     * @param args Formatting arguments
     * @return Safe MutableComponent combining prefix and message
     */
    @NotNull
    public static MutableComponent getPrefixedMessage(@Nullable String key, @Nullable Object... args) {
        return getPrefix().append(getMessage(key, args));
    }

    /**
     * Resolves a translated string directly on the current JVM (server or client).
     * Falls back to the key or default value if the key does not exist.
     *
     * @param key  Translation key
     * @param args Formatting arguments
     * @return Formatted raw string
     */
    @NotNull
    public static String getRawTranslation(@Nullable String key, @Nullable Object... args) {
        if (key == null || key.isBlank()) {
            return "instaff.error.missing_key";
        }

        Language language = Language.getInstance();
        String formatString = language.getOrDefault(key, key);
        Object[] sanitizedArgs = sanitizeArgs(args);

        if (sanitizedArgs.length == 0) {
            return formatString;
        }

        try {
            return String.format(formatString, sanitizedArgs);
        } catch (Exception e) {
            return formatString;
        }
    }

    /**
     * Helper to sanitize argument arrays against null elements and null array.
     */
    @NotNull
    private static Object[] sanitizeArgs(@Nullable Object... args) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitized[i] = (args[i] != null) ? args[i] : "null";
        }
        return sanitized;
    }
}
