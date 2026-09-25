package com.tio.instaff.client.integrity;

import com.tio.instaff.InStaff;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-only utility to securely query hardware machine identifiers across operating systems
 * and derive a deterministic SHA-256 installation token.
 *
 * <p>Security & Privacy Guarantees:
 * <ul>
 *   <li>Raw machine identifiers are NEVER logged at any log level (DEBUG, INFO, WARN, ERROR).</li>
 *   <li>Raw identifiers are NEVER embedded into exception messages or stack traces.</li>
 *   <li>External queries strictly timeout after 1 second to prevent blocking client execution.</li>
 *   <li>One-way SHA-256 derivation makes preimage recovery computationally infeasible.</li>
 * </ul>
 */
public final class MachineIdFetcher {

    private static final String SALT_PREFIX = "instaff-token-v1:";
    private static final long PROCESS_TIMEOUT_SECONDS = 1L;

    static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    static final Pattern LINUX_MACHINE_ID_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");
    static final Pattern MAC_IOREG_PATTERN = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([0-9a-fA-F-]{36})\"");

    private MachineIdFetcher() {
    }

    /**
     * Safely attempts to query the host OS for a stable hardware machine identifier.
     *
     * @return Optional containing the raw identifier if retrieved, or empty if unavailable/unsupported/timed out.
     */
    @NotNull
    public static Optional<String> fetchRawMachineIdentifier() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) {
                return fetchWindowsMachineGuid();
            } else if (osName.contains("linux")) {
                return fetchLinuxMachineId();
            } else if (osName.contains("mac") || osName.contains("darwin")) {
                return fetchMacPlatformUuid();
            }
            InStaff.LOGGER.debug("[In-Staff] Unsupported OS for deterministic machine identifier derivation.");
            return Optional.empty();
        } catch (Throwable t) {
            InStaff.LOGGER.debug("[In-Staff] Unexpected exception querying machine identifier: {}", t.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Derives a deterministic 36-character UUID token from a raw machine identifier using SHA-256.
     *
     * @param rawIdentifier The raw machine identifier.
     * @return A persistent UUID string formatted as 8-4-4-4-12.
     */
    @NotNull
    public static String toSha256Token(@Nullable String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return UUID.randomUUID().toString();
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] inputBytes = (SALT_PREFIX + rawIdentifier.trim()).getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(inputBytes);

            ByteBuffer buffer = ByteBuffer.wrap(hash);
            long mostSig = buffer.getLong();
            long leastSig = buffer.getLong();
            return new UUID(mostSig, leastSig).toString();
        } catch (Throwable t) {
            InStaff.LOGGER.error("[In-Staff] Cryptographic error computing token hash, falling back to random UUID", t);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Windows: Queries HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid via reg query.
     */
    @NotNull
    private static Optional<String> fetchWindowsMachineGuid() {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                    "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid");
            pb.redirectErrorStream(true);
            process = pb.start();

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                InStaff.LOGGER.warn("[In-Staff] Windows registry query timed out after {}s.", PROCESS_TIMEOUT_SECONDS);
                return Optional.empty();
            }

            if (process.exitValue() != 0) {
                return Optional.empty();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                return extractUuidFromWindowsOutput(output.toString());
            }
        } catch (Throwable t) {
            InStaff.LOGGER.debug("[In-Staff] Failed to execute reg query: {}", t.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Parses Windows reg query output safely without leaking input in exceptions.
     */
    @NotNull
    static Optional<String> extractUuidFromWindowsOutput(@Nullable String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = UUID_PATTERN.matcher(output);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    /**
     * Linux: Reads /etc/machine-id or fallback /var/lib/dbus/machine-id.
     */
    @NotNull
    private static Optional<String> fetchLinuxMachineId() {
        try {
            Path primaryPath = Path.of("/etc/machine-id");
            if (Files.exists(primaryPath) && Files.isReadable(primaryPath)) {
                String id = Files.readString(primaryPath, StandardCharsets.UTF_8).trim();
                return sanitizeLinuxMachineId(id);
            }

            Path fallbackPath = Path.of("/var/lib/dbus/machine-id");
            if (Files.exists(fallbackPath) && Files.isReadable(fallbackPath)) {
                String id = Files.readString(fallbackPath, StandardCharsets.UTF_8).trim();
                return sanitizeLinuxMachineId(id);
            }
        } catch (Throwable t) {
            InStaff.LOGGER.debug("[In-Staff] Failed to read Linux machine-id: {}", t.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    /**
     * Validates and sanitizes Linux machine ID output.
     */
    @NotNull
    static Optional<String> sanitizeLinuxMachineId(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (LINUX_MACHINE_ID_PATTERN.matcher(trimmed).matches() || UUID_PATTERN.matcher(trimmed).matches()) {
            return Optional.of(trimmed);
        }
        return Optional.empty();
    }

    /**
     * macOS: Queries IOPlatformExpertDevice via ioreg.
     */
    @NotNull
    private static Optional<String> fetchMacPlatformUuid() {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
            pb.redirectErrorStream(true);
            process = pb.start();

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                InStaff.LOGGER.warn("[In-Staff] macOS ioreg query timed out after {}s.", PROCESS_TIMEOUT_SECONDS);
                return Optional.empty();
            }

            if (process.exitValue() != 0) {
                return Optional.empty();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                return extractUuidFromMacOutput(output.toString());
            }
        } catch (Throwable t) {
            InStaff.LOGGER.debug("[In-Staff] Failed to execute ioreg: {}", t.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Parses macOS ioreg output safely without leaking input in exceptions.
     */
    @NotNull
    static Optional<String> extractUuidFromMacOutput(@Nullable String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = MAC_IOREG_PATTERN.matcher(output);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        // Fallback to generic UUID match if exact property syntax differs slightly
        Matcher fallbackMatcher = UUID_PATTERN.matcher(output);
        if (fallbackMatcher.find()) {
            return Optional.of(fallbackMatcher.group());
        }
        return Optional.empty();
    }
}
