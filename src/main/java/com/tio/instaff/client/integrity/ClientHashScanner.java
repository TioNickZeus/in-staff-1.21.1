package com.tio.instaff.client.integrity;

import com.tio.instaff.InStaff;
import com.tio.instaff.network.IntegrityRequestPayload;
import com.tio.instaff.network.IntegrityResponsePayload;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/**
 * Client-side scanner for mod integrity and token binding.
 * Scans local mods/ directory, computes SHA-256 file hashes,
 * and maintains a persistent installation token to deter offline ban evasion.
 * This class is strictly CLIENT-side and must never be loaded by dedicated servers.
 */
public final class ClientHashScanner {

    private static final String TOKEN_FILE = ".instaff_token";
    private static volatile String cachedToken = null;

    private ClientHashScanner() {
    }

    /**
     * Retrieves or generates a persistent installation token unique to this client installation.
     */
    @NotNull
    public static synchronized String getOrCreateInstallationToken() {
        if (cachedToken != null) {
            return cachedToken;
        }

        Path tokenPath = FMLPaths.GAMEDIR.get().resolve(TOKEN_FILE);
        try {
            if (Files.exists(tokenPath)) {
                String read = Files.readString(tokenPath).trim();
                if (!read.isBlank()) {
                    cachedToken = read;
                    return cachedToken;
                }
            }

            String newToken = UUID.randomUUID().toString();
            Files.writeString(tokenPath, newToken);
            cachedToken = newToken;
            return cachedToken;
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to read/write In-Staff client installation token, using transient token", e);
            cachedToken = UUID.randomUUID().toString();
            return cachedToken;
        }
    }

    /**
     * Computes the SHA-256 hash of a file.
     */
    @NotNull
    public static String computeFileSha256(@NotNull Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to compute SHA-256 for file: {}", filePath, e);
            return "";
        }
    }

    /**
     * Scans the client's mods/ directory and computes hashes for all .jar files.
     */
    @NotNull
    public static Map<String, String> scanModsDirectory() {
        Map<String, String> hashes = new HashMap<>();
        Path modsDir = FMLPaths.MODSDIR.get();

        if (!Files.exists(modsDir)) {
            return hashes;
        }

        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(p -> p.toString().endsWith(".jar") && Files.isRegularFile(p))
                    .forEach(jarPath -> {
                        String filename = jarPath.getFileName().toString();
                        String sha256 = computeFileSha256(jarPath);
                        if (!sha256.isBlank()) {
                            hashes.put(filename, sha256);
                        }
                    });
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to scan mods directory for integrity hashes", e);
        }

        return hashes;
    }

    /**
     * Collects all loaded mod IDs from ModList.
     */
    @NotNull
    public static List<String> collectLoadedModIds() {
        List<String> list = new ArrayList<>();
        try {
            ModList.get().getMods().forEach(mod -> list.add(mod.getModId()));
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to collect loaded mod IDs", e);
        }
        return list;
    }

    /**
     * Handles the server's integrity request on the client side:
     * scans the filesystem, gathers token, and responds with IntegrityResponsePayload.
     */
    public static void handleIntegrityRequest(@NotNull IntegrityRequestPayload request) {
        InStaff.LOGGER.info("Received integrity verification request from server. Initiating client scan...");

        String token = getOrCreateInstallationToken();
        Map<String, String> hashes = scanModsDirectory();
        List<String> loadedModIds = collectLoadedModIds();

        IntegrityResponsePayload response = new IntegrityResponsePayload(token, hashes, loadedModIds);
        PacketDistributor.sendToServer(response);

        InStaff.LOGGER.info("Client integrity scan complete. Sent {} file hashes and {} loaded mod IDs to server.",
                hashes.size(), loadedModIds.size());
    }
}
