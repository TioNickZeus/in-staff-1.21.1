package com.tio.instaff.access;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tio.instaff.InStaff;
import com.tio.instaff.config.InStaffConfig;
import com.tio.instaff.util.FileStorageUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State engine and security coordinator for Staff Device Lock (Anti-Impersonation Binding).
 * Binds OP-level accounts to console-authorized client installation tokens.
 * Quarantines commands during the pre-verification window and fails closed on data corruption.
 */
public final class DeviceLockManager {

    private static final DeviceLockManager INSTANCE = new DeviceLockManager();
    private static final String FILE_NAME = "device_locks.json";

    private final Object lock = new Object();
    private final Object ioLock = new Object();

    private final Map<UUID, DeviceLockRecord> bindings = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDeviceLocks = ConcurrentHashMap.newKeySet();
    private volatile boolean isCorruptedState = false;
    private static volatile Boolean configOverrideForTesting = null;

    private DeviceLockManager() {
        load();
    }

    public static DeviceLockManager getInstance() {
        return INSTANCE;
    }

    public static void setConfigOverrideForTesting(@Nullable Boolean override) {
        configOverrideForTesting = override;
    }

    private boolean isFeatureEnabled() {
        if (configOverrideForTesting != null) {
            return configOverrideForTesting;
        }
        return InStaffConfig.isDeviceLockEnabled();
    }

    private Path getStoragePath() {
        return FileStorageUtil.getDataDirectory().resolve(FILE_NAME);
    }

    /**
     * Centralized staff-resolution function.
     * Evaluates whether a player possesses operator permissions (OP level >= 2).
     * Used identically at both login-time marking and handshake verification.
     */
    public static boolean isStaffAccount(@Nullable ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        return server.getPlayerList().isOp(player.getGameProfile()) ||
                server.getProfilePermissions(player.getGameProfile()) >= 2;
    }

    /**
     * Loads bindings from disk. If the database file is corrupted, enters a fail-closed
     * state and creates a timestamped backup copy so that staff accounts are protected.
     */
    public void load() {
        synchronized (lock) {
            bindings.clear();
            Path storagePath = getStoragePath();
            if (!Files.exists(storagePath)) {
                isCorruptedState = false;
                return;
            }

            try (BufferedReader reader = Files.newBufferedReader(storagePath)) {
                Type listType = new TypeToken<List<DeviceLockRecord>>() {}.getType();
                List<DeviceLockRecord> loaded = FileStorageUtil.GSON.fromJson(reader, listType);

                if (loaded != null) {
                    for (DeviceLockRecord record : loaded) {
                        if (record != null && record.getAccountUUID() != null) {
                            bindings.put(record.getAccountUUID(), record);
                        }
                    }
                }
                isCorruptedState = false;
                InStaff.LOGGER.info("Loaded {} staff device lock bindings.", bindings.size());
            } catch (JsonParseException | IOException e) {
                isCorruptedState = true;
                InStaff.LOGGER.error("Device locks database file {} is corrupted! Entering fail-closed security state. All OP-level logins will be rejected.", storagePath, e);
                Path corruptBackup = storagePath.resolveSibling(storagePath.getFileName() + ".corrupt." + System.currentTimeMillis());
                try {
                    Files.copy(storagePath, corruptBackup, StandardCopyOption.REPLACE_EXISTING);
                    InStaff.LOGGER.info("Created emergency backup of corrupted device locks at {}", corruptBackup);
                } catch (IOException copyEx) {
                    InStaff.LOGGER.error("Failed to create emergency backup of corrupted device locks file: {}", storagePath, copyEx);
                }
            }
        }
    }

    /**
     * Atomically saves all registered device lock bindings to disk.
     */
    public void save() {
        List<DeviceLockRecord> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(bindings.values());
        }
        CompletableFuture.runAsync(() -> {
            synchronized (ioLock) {
                FileStorageUtil.saveAtomicJson(getStoragePath(), snapshot);
            }
        });
    }

    /**
     * Synchronous save for console commands or test fixtures where immediate persistence is required.
     */
    public void saveSync() {
        List<DeviceLockRecord> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(bindings.values());
        }
        synchronized (ioLock) {
            FileStorageUtil.saveAtomicJson(getStoragePath(), snapshot);
        }
    }

    /**
     * Evaluates whether a connecting player needs command quarantine during handshake evaluation.
     * Only activates for OP-level accounts when the feature is enabled and an explicit binding exists.
     */
    public void onPlayerLoggedIn(@NotNull ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (isFeatureEnabled() && isStaffAccount(player) && hasBinding(uuid)) {
            pendingDeviceLocks.add(uuid);
            InStaff.LOGGER.info("Quarantined commands for staff member {} ({}) pending device lock verification.",
                    player.getGameProfile().getName(), uuid);
        }
    }

    /**
     * Secondary safety net: clears quarantine state if the player disconnects before validation completes.
     */
    public void onPlayerLoggedOut(@NotNull UUID uuid) {
        pendingDeviceLocks.remove(uuid);
    }

    /**
     * Checks if a player's commands are currently quarantined pending device lock verification.
     */
    public boolean isPendingDeviceLock(@Nullable UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return pendingDeviceLocks.contains(uuid);
    }

    /**
     * Evaluates a client's installation token against stored console bindings.
     * Unconditionally clears the pending quarantine flag before returning in every single terminal branch.
     *
     * @param uuid    Target player UUID
     * @param name    Player name
     * @param token   Presented installation token from IntegrityResponsePayload
     * @param isStaff Whether the player has OP permissions
     * @return Terminal outcome (NOT_APPLICABLE, CORRUPTED, NO_BINDING, MATCH, MISMATCH)
     */
    @NotNull
    public DeviceValidationResult validateAndRecord(@NotNull UUID uuid, @Nullable String name, @Nullable String token, boolean isStaff) {
        // Proactive quarantine cleanup: always clear pending flag first in every outcome
        pendingDeviceLocks.remove(uuid);

        if (!isStaff || !isFeatureEnabled()) {
            return DeviceValidationResult.NOT_APPLICABLE;
        }

        if (isCorruptedState) {
            return DeviceValidationResult.CORRUPTED;
        }

        DeviceLockRecord record = bindings.get(uuid);
        if (record == null) {
            return DeviceValidationResult.NO_BINDING;
        }

        if (token != null && !token.isBlank() && token.equals(record.getBoundToken())) {
            record.updateLastSeen();
            if (name != null && !name.isBlank()) {
                record.setLastAccountName(name);
            }
            save();
            return DeviceValidationResult.MATCH;
        } else {
            return DeviceValidationResult.MISMATCH;
        }
    }

    /**
     * Direct console entry point to bind a staff account to an installation token.
     */
    public boolean bindDirect(@NotNull UUID targetUUID, @NotNull String name, @NotNull String token) {
        synchronized (lock) {
            DeviceLockRecord record = bindings.get(targetUUID);
            if (record != null) {
                record.setBoundToken(token);
                record.setLastAccountName(name);
                record.updateLastSeen();
            } else {
                record = new DeviceLockRecord(targetUUID, name, token);
                bindings.put(targetUUID, record);
            }
            saveSync();
            return true;
        }
    }

    /**
     * Direct console entry point to remove a staff account binding.
     */
    public boolean unbindDirect(@NotNull UUID targetUUID) {
        synchronized (lock) {
            DeviceLockRecord removed = bindings.remove(targetUUID);
            if (removed != null) {
                saveSync();
                return true;
            }
            return false;
        }
    }

    public boolean hasBinding(@NotNull UUID uuid) {
        return bindings.containsKey(uuid);
    }

    @NotNull
    public Optional<DeviceLockRecord> getRecord(@NotNull UUID targetUUID) {
        return Optional.ofNullable(bindings.get(targetUUID));
    }

    @NotNull
    public List<DeviceLockRecord> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(bindings.values()));
    }

    public boolean isCorruptedState() {
        return isCorruptedState;
    }

    public void setCorruptedStateForTesting(boolean corrupted) {
        this.isCorruptedState = corrupted;
    }

    public void markPendingForTesting(@NotNull UUID uuid) {
        this.pendingDeviceLocks.add(uuid);
    }

    public void resetForTesting() {
        synchronized (lock) {
            bindings.clear();
            pendingDeviceLocks.clear();
            isCorruptedState = false;
        }
    }
}
