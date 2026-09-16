package com.tio.instaff.moderation;

import com.google.gson.reflect.TypeToken;
import com.tio.instaff.InStaff;
import com.tio.instaff.config.InStaffConfig;
import com.tio.instaff.util.FileStorageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

/**
 * Thread-safe moderation engine managing bans, mutes, kicks, freezes, and audit history.
 * Employs atomic file persistence and indexing for offline ban evasion deterrence.
 */
public final class PunishmentManager {

    private static final PunishmentManager INSTANCE = new PunishmentManager();
    private static final String FILE_NAME = "punishments_history.json";

    private final Object lock = new Object();
    private final List<PunishmentRecord> history = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, PunishmentRecord> activeBans = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, PunishmentRecord> activeMutes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> frozenPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private PunishmentManager() {
        load();
    }

    public static PunishmentManager getInstance() {
        return INSTANCE;
    }

    private Path getStoragePath() {
        return FileStorageUtil.getDataDirectory().resolve(FILE_NAME);
    }

    /**
     * Loads punishment records from disk and rebuilds active indexes.
     */
    public void load() {
        synchronized (lock) {
            history.clear();
            activeBans.clear();
            activeMutes.clear();
            frozenPlayers.clear();

            Type listType = new TypeToken<List<PunishmentRecord>>() {}.getType();
            List<PunishmentRecord> loaded = FileStorageUtil.loadJson(getStoragePath(), listType);

            if (loaded != null) {
                for (PunishmentRecord record : loaded) {
                    if (record == null) continue;
                    history.add(record);
                    if (record.isActive()) {
                        indexActive(record);
                    }
                }
            }
            InStaff.LOGGER.info("Loaded {} punishment records ({} active bans, {} active mutes, {} frozen).",
                    history.size(), activeBans.size(), activeMutes.size(), frozenPlayers.size());
        }
    }

    /**
     * Atomically saves all punishment history to disk.
     */
    public void save() {
        synchronized (lock) {
            FileStorageUtil.saveAtomicJson(getStoragePath(), history);
        }
    }

    private void indexActive(PunishmentRecord record) {
        if (record.getType().isBan()) {
            activeBans.put(record.getTargetUUID(), record);
        } else if (record.getType().isMute()) {
            activeMutes.put(record.getTargetUUID(), record);
        } else if (record.getType().isFreeze()) {
            frozenPlayers.add(record.getTargetUUID());
        }
    }

    /**
     * Issues a new punishment, indexing it in memory and saving to disk.
     */
    public void addPunishment(@NotNull PunishmentRecord record) {
        synchronized (lock) {
            history.add(record);
            if (record.isActive()) {
                indexActive(record);
            }
            save();
        }
    }

    /**
     * Revokes an active ban for the specified target UUID.
     */
    public boolean unban(@NotNull UUID targetUUID, @Nullable UUID staffUUID, @Nullable String staffName, @Nullable String reason) {
        synchronized (lock) {
            PunishmentRecord record = activeBans.remove(targetUUID);
            if (record != null && record.isActive()) {
                record.revoke(staffUUID, staffName, reason);
                save();
                return true;
            }
            return false;
        }
    }

    /**
     * Revokes an active mute for the specified target UUID.
     */
    public boolean unmute(@NotNull UUID targetUUID, @Nullable UUID staffUUID, @Nullable String staffName, @Nullable String reason) {
        synchronized (lock) {
            PunishmentRecord record = activeMutes.remove(targetUUID);
            if (record != null && record.isActive()) {
                record.revoke(staffUUID, staffName, reason);
                save();
                return true;
            }
            return false;
        }
    }

    /**
     * Sets or clears frozen status for a player.
     */
    public void setFrozen(@NotNull UUID targetUUID, @NotNull String targetName, @Nullable UUID staffUUID, @Nullable String staffName, boolean freeze, @Nullable String reason) {
        synchronized (lock) {
            if (freeze) {
                frozenPlayers.add(targetUUID);
                PunishmentRecord record = new PunishmentRecord(
                        targetUUID, targetName, staffUUID, staffName,
                        PunishmentType.FREEZE, reason, -1L, null, null
                );
                history.add(record);
            } else {
                frozenPlayers.remove(targetUUID);
                // Mark active freeze records as revoked
                for (PunishmentRecord r : history) {
                    if (r.getTargetUUID().equals(targetUUID) && r.getType() == PunishmentType.FREEZE && r.isActive()) {
                        r.revoke(staffUUID, staffName, reason);
                    }
                }
            }
            save();
        }
    }

    public boolean isFrozen(@NotNull UUID targetUUID) {
        return frozenPlayers.contains(targetUUID);
    }

    /**
     * Retrieves an active ban for the target UUID, expiring it if duration lapsed.
     */
    public Optional<PunishmentRecord> getActiveBan(@NotNull UUID targetUUID) {
        PunishmentRecord record = activeBans.get(targetUUID);
        if (record != null) {
            if (record.isExpired()) {
                synchronized (lock) {
                    if (activeBans.remove(targetUUID, record)) {
                        save();
                    }
                }
                return Optional.empty();
            }
            return Optional.of(record);
        }
        return Optional.empty();
    }

    /**
     * Checks if an IP address belongs to an active banned player (offline ban evasion check).
     */
    public Optional<PunishmentRecord> getActiveBanByIp(@Nullable String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || !InStaffConfig.isPreventOfflineBanEvasion()) {
            return Optional.empty();
        }

        for (PunishmentRecord record : activeBans.values()) {
            if (record.isActive() && ipAddress.equals(record.getIpAddress())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if a client token belongs to an active banned player (offline ban evasion check).
     */
    public Optional<PunishmentRecord> getActiveBanByClientToken(@Nullable String token) {
        if (token == null || token.isBlank() || !InStaffConfig.isPreventOfflineBanEvasion()) {
            return Optional.empty();
        }

        for (PunishmentRecord record : activeBans.values()) {
            if (record.isActive() && token.equals(record.getClientToken())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves an active mute for the target UUID, expiring it if duration lapsed.
     */
    public Optional<PunishmentRecord> getActiveMute(@NotNull UUID targetUUID) {
        PunishmentRecord record = activeMutes.get(targetUUID);
        if (record != null) {
            if (record.isExpired()) {
                synchronized (lock) {
                    if (activeMutes.remove(targetUUID, record)) {
                        save();
                    }
                }
                return Optional.empty();
            }
            return Optional.of(record);
        }
        return Optional.empty();
    }

    /**
     * Returns an unmodifiable list of all punishment records targeting the specified UUID.
     */
    @NotNull
    public List<PunishmentRecord> getHistory(@NotNull UUID targetUUID) {
        List<PunishmentRecord> list = new ArrayList<>();
        for (PunishmentRecord r : history) {
            if (r.getTargetUUID().equals(targetUUID)) {
                list.add(r);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the most recent client installation token recorded for this player, if any.
     * Used as a fallback when the player is not connected and the integrity validator
     * holds no live token for them.
     */
    @Nullable
    public String findLastKnownClientToken(@NotNull UUID targetUUID) {
        for (int i = history.size() - 1; i >= 0; i--) {
            PunishmentRecord r = history.get(i);
            if (targetUUID.equals(r.getTargetUUID())) {
                String token = r.getClientToken();
                if (token != null && !token.isBlank()) {
                    return token;
                }
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable copy of all historical punishment records.
     */
    @NotNull
    public List<PunishmentRecord> getAllHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}
