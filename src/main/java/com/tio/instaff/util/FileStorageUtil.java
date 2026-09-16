package com.tio.instaff.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tio.instaff.InStaff;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Defensive file storage utility for In-Staff.
 * Provides atomic file writes (.tmp swap), safe JSON serialization,
 * and zero-crash exception handling for all persistence subsystems.
 */
public final class FileStorageUtil {

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private FileStorageUtil() {
    }

    private static volatile Path customDataDirectory = null;

    public static void setDataDirectoryForTesting(@Nullable Path path) {
        customDataDirectory = path;
    }

    /**
     * Resolves the base In-Staff data storage directory (<gameDir>/instaff/).
     * Falls back to working directory if FMLPaths is not initialized (e.g., in unit tests).
     */
    @NotNull
    public static Path getDataDirectory() {
        if (customDataDirectory != null) {
            return customDataDirectory;
        }

        Path baseDir;
        try {
            if (FMLPaths.GAMEDIR != null && FMLPaths.GAMEDIR.get() != null) {
                baseDir = FMLPaths.GAMEDIR.get();
            } else {
                baseDir = Path.of(".");
            }
        } catch (Throwable t) {
            baseDir = Path.of(".");
        }

        Path dir = baseDir.resolve("instaff");
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            InStaff.LOGGER.error("Failed to create In-Staff data directory: {}", dir, e);
        }
        return dir;
    }

    /**
     * Atomically saves an object as JSON to the target file.
     * Writes to target.tmp first, then replaces target using atomic move.
     *
     * @param targetFile Target file path
     * @param data       Object to serialize
     * @return true if save succeeded, false otherwise
     */
    public static boolean saveAtomicJson(@NotNull Path targetFile, @NotNull Object data) {
        Path parent = targetFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                InStaff.LOGGER.error("Failed to create parent directories for file: {}", targetFile, e);
                return false;
            }
        }

        Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");

        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            GSON.toJson(data, writer);
            writer.flush();
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to write temporary JSON file: {}", tempFile, e);
            tryDelete(tempFile);
            return false;
        }

        try {
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            InStaff.LOGGER.error("Failed to atomically move {} to {}", tempFile, targetFile, e);
            tryDelete(tempFile);
            return false;
        }
    }

    /**
     * Safely reads and deserializes JSON data from target file.
     * If the file is missing, returns null.
     * If the file is corrupted, creates a .corrupted.bak backup and returns null.
     *
     * @param targetFile Target file path
     * @param typeOfT    Target type / Class
     * @param <T>        Result type
     * @return Deserialized object, or null on missing/failure
     */
    @Nullable
    public static <T> T loadJson(@NotNull Path targetFile, @NotNull Type typeOfT) {
        if (!Files.exists(targetFile)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(targetFile)) {
            return GSON.fromJson(reader, typeOfT);
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to parse JSON file {}. Creating backup.", targetFile, e);
            Path corruptedBackup = targetFile.resolveSibling(targetFile.getFileName() + ".corrupted.bak");
            try {
                Files.copy(targetFile, corruptedBackup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                InStaff.LOGGER.error("Failed to create backup of corrupted file {}", targetFile, ex);
            }
            return null;
        }
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
