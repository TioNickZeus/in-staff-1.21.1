package com.tio.instaff.util;

import net.minecraft.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust parser for human-readable duration strings (e.g. "1d12h", "30m", "45s", "1w", "perm").
 * Strictly enforces monotonic time through net.minecraft.Util.getMillis().
 */
public final class DurationParser {

    public static final long PERMANENT = -1L;

    private static final long SECOND_MS = 1_000L;
    private static final long MINUTE_MS = 60 * SECOND_MS;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long WEEK_MS = 7 * DAY_MS;
    private static final long MONTH_MS = 30 * DAY_MS;
    private static final long YEAR_MS = 365 * DAY_MS;

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(\\d+)\\s*(mo|months?|m|mins?|minutes?|h|hours?|d|days?|w|weeks?|y|years?|s|secs?|seconds?)"
    );

    private DurationParser() {
    }

    /**
     * Parses a duration string to milliseconds.
     * Returns OptionalLong.of(-1L) if permanent/never/perm.
     * Returns OptionalLong.empty() if invalid, empty, or overflowed.
     */
    public static OptionalLong tryParseDurationMillis(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }

        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("-1") || trimmed.equals("never")) {
            return OptionalLong.of(PERMANENT);
        }

        Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
        long totalMillis = 0;
        int lastEnd = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            // Ensure no invalid characters exist between matched tokens
            String intermediate = trimmed.substring(lastEnd, matcher.start()).trim();
            if (!intermediate.isEmpty()) {
                return OptionalLong.empty();
            }

            foundAny = true;
            lastEnd = matcher.end();

            try {
                long amount = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();

                long unitMillis = getUnitMultiplier(unit);
                long product = Math.multiplyExact(amount, unitMillis);
                totalMillis = Math.addExact(totalMillis, product);
            } catch (ArithmeticException | NumberFormatException e) {
                return OptionalLong.empty();
            }
        }

        // Ensure trailing characters are strictly whitespace
        if (!foundAny || !trimmed.substring(lastEnd).trim().isEmpty()) {
            return OptionalLong.empty();
        }

        if (totalMillis <= 0) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(totalMillis);
    }

    /**
     * Parses a duration string to milliseconds, throwing IllegalArgumentException if invalid.
     */
    public static long parseDurationMillis(@NotNull String input) throws IllegalArgumentException {
        return tryParseDurationMillis(input)
                .orElseThrow(() -> new IllegalArgumentException("Invalid duration string: '" + input + "'"));
    }

    /**
     * Calculates the future expiration timestamp in monotonic milliseconds (Util.getMillis() + duration).
     * If duration is permanent, returns PERMANENT (-1L).
     */
    public static OptionalLong tryParseTargetExpiryMillis(@Nullable String input) {
        OptionalLong duration = tryParseDurationMillis(input);
        if (duration.isEmpty()) {
            return OptionalLong.empty();
        }

        long millis = duration.getAsLong();
        if (millis == PERMANENT) {
            return OptionalLong.of(PERMANENT);
        }

        return OptionalLong.of(Util.getMillis() + millis);
    }

    /**
     * Formats duration in milliseconds into a concise human-readable string (e.g. "1d 12h 30m").
     */
    @NotNull
    public static String formatDuration(long millis) {
        if (millis == PERMANENT) {
            return LocalizationHelper.getRawTranslation("instaff.common.permanent");
        }
        if (millis <= 0) {
            return "0s";
        }

        long remainder = millis;

        long days = remainder / DAY_MS;
        remainder %= DAY_MS;

        long hours = remainder / HOUR_MS;
        remainder %= HOUR_MS;

        long minutes = remainder / MINUTE_MS;
        remainder %= MINUTE_MS;

        long seconds = remainder / SECOND_MS;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Formats remaining time until expiration (monotonic targetExpiryMillis - Util.getMillis()).
     */
    @NotNull
    public static String formatRemaining(long targetExpiryMillis) {
        if (targetExpiryMillis == PERMANENT) {
            return LocalizationHelper.getRawTranslation("instaff.common.permanent");
        }

        long now = Util.getMillis();
        long diff = targetExpiryMillis - now;
        if (diff <= 0) {
            return "0s";
        }
        return formatDuration(diff);
    }

    private static long getUnitMultiplier(String unit) {
        if (unit.startsWith("mo")) return MONTH_MS;
        if (unit.startsWith("m")) return MINUTE_MS;
        if (unit.startsWith("h")) return HOUR_MS;
        if (unit.startsWith("d")) return DAY_MS;
        if (unit.startsWith("w")) return WEEK_MS;
        if (unit.startsWith("y")) return YEAR_MS;
        if (unit.startsWith("s")) return SECOND_MS;
        throw new IllegalArgumentException("Unknown time unit: " + unit);
    }
}
