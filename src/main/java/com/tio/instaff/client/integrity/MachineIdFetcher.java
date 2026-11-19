package com.tio.instaff.client.integrity;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tio.instaff.InStaff;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Client-only utility to collect a 100% in-memory native JVM/Minecraft fingerprint
 * and derive a server-scoped deterministic SHA-256 client token.
 *
 * <p>Security & Privacy Guarantees:
 * <ul>
 *   <li>Zero external OS process calls (no reg query, ioreg, or shell execution).</li>
 *   <li>Zero OS machine-id file reads and zero local token files written to disk.</li>
 *   <li>Raw machine signals are NEVER logged at any log level or embedded in exceptions.</li>
 *   <li>One-way SHA-256 derivation combined with the server's serverKey ensures cross-server privacy.</li>
 * </ul>
 */
public final class MachineIdFetcher {

    private static final String SALT_PREFIX = "instaff-token-v2:";

    private static volatile String cachedBaseFingerprint = null;
    private static volatile String cachedGpuSignature = "";

    private MachineIdFetcher() {
    }

    /**
     * Safely captures OpenGL GPU vendor and renderer strings if invoked on the client render thread.
     * Falls back silently to an empty signature outside render contexts.
     */
    public static void captureGpuInfoOnRenderThread() {
        try {
            if (RenderSystem.isOnRenderThread()) {
                String vendor = safeStr(GlUtil.getVendor());
                String renderer = safeStr(GlUtil.getRenderer());
                if (!vendor.isEmpty() || !renderer.isEmpty()) {
                    cachedGpuSignature = "gpu=" + vendor + "/" + renderer;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Collects a deterministic in-memory composite fingerprint using standard Java 21 APIs
     * plus any captured OpenGL GPU signature.
     *
     * @return Non-empty composite native fingerprint string (never logged or transmitted directly).
     */
    @NotNull
    public static String collectNativeFingerprint() {
        String base = cachedBaseFingerprint;
        if (base == null || base.isBlank()) {
            base = buildBaseFingerprint();
            cachedBaseFingerprint = base;
        }

        String gpu = cachedGpuSignature;
        if (gpu != null && !gpu.isEmpty()) {
            return base + "|" + gpu;
        }
        return base;
    }

    /**
     * Derives a deterministic 36-character UUID token from a serverKey and a native fingerprint
     * using SHA-256("instaff-token-v2:" + serverKey + "|" + nativeFingerprint).
     *
     * @param serverKey         Server-provided 10-character key.
     * @param nativeFingerprint In-memory client fingerprint.
     * @return A deterministic UUID string formatted as 8-4-4-4-12, or a random UUID if inputs are blank.
     */
    @NotNull
    public static String deriveToken(@Nullable String serverKey, @Nullable String nativeFingerprint) {
        if (serverKey == null || serverKey.isBlank()
                || nativeFingerprint == null || nativeFingerprint.isBlank()) {
            return UUID.randomUUID().toString();
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String composite = SALT_PREFIX + serverKey.trim() + "|" + nativeFingerprint.trim();
            byte[] hash = digest.digest(composite.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.wrap(hash);
            long mostSig = buffer.getLong();
            long leastSig = buffer.getLong();
            return new UUID(mostSig, leastSig).toString();
        } catch (Throwable t) {
            InStaff.LOGGER.error("[In-Staff] Cryptographic error computing client token hash ({}), falling back to random UUID",
                    t.getClass().getSimpleName());
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Collects the host native fingerprint in memory and derives the token for the given serverKey.
     *
     * @param serverKey Server-provided key from IntegrityRequestPayload.
     * @return Deterministic 36-character UUID token scoped to this machine and serverKey.
     */
    @NotNull
    public static String generateClientToken(@Nullable String serverKey) {
        return deriveToken(serverKey, collectNativeFingerprint());
    }

    @NotNull
    private static String buildBaseFingerprint() {
        String procId = readEnvSafe("PROCESSOR_IDENTIFIER");
        String procArch = readEnvSafe("PROCESSOR_ARCHITECTURE");
        String osArch = readPropSafe("os.arch");
        int cores = readCoresSafe();
        long totalRam = readTotalRamSafe();
        long totalDisk = readTotalDiskSafe();
        String osName = readPropSafe("os.name");
        String userName = readPropSafe("user.name");

        return "cpu=" + procId
                + "|arch=" + procArch
                + "|osArch=" + osArch
                + "|cores=" + cores
                + "|ram=" + totalRam
                + "|disk=" + totalDisk
                + "|os=" + osName
                + "|user=" + userName;
    }

    private static String readEnvSafe(String name) {
        try {
            return safeStr(System.getenv(name));
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readPropSafe(String name) {
        try {
            return safeStr(System.getProperty(name));
        } catch (Throwable t) {
            return "";
        }
    }

    private static int readCoresSafe() {
        try {
            return Runtime.getRuntime().availableProcessors();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long readTotalRamSafe() {
        try {
            OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();
            if (mxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                return sunBean.getTotalMemorySize();
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static long readTotalDiskSafe() {
        try {
            String home = System.getProperty("user.home", ".");
            long space = new File(home).getTotalSpace();
            if (space > 0L) {
                return space;
            }
            return new File(".").getTotalSpace();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String safeStr(@Nullable String value) {
        return value != null ? value.trim() : "";
    }
}
