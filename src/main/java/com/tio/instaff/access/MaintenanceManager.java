package com.tio.instaff.access;

import com.google.gson.reflect.TypeToken;
import com.tio.instaff.InStaff;
import com.tio.instaff.config.InStaffConfig;
import com.tio.instaff.util.FileStorageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe manager for dynamic server maintenance mode.
 * Restricts player joins, controls maintenance MOTD, and allows staff bypass.
 */
public final class MaintenanceManager {

    private static final MaintenanceManager INSTANCE = new MaintenanceManager();
    private static final String FILE_NAME = "maintenance.json";

    private final Object lock = new Object();
    private boolean enabled;
    private final Set<UUID> staffBypass = new HashSet<>();
    private String customMotd;

    private static class MaintenanceSaveData {
        boolean enabled;
        Set<UUID> staffBypass = new HashSet<>();
        String customMotd;
    }

    private MaintenanceManager() {
        load();
    }

    public static MaintenanceManager getInstance() {
        return INSTANCE;
    }

    private Path getStoragePath() {
        return FileStorageUtil.getDataDirectory().resolve(FILE_NAME);
    }

    public void load() {
        synchronized (lock) {
            staffBypass.clear();
            Type type = new TypeToken<MaintenanceSaveData>() {}.getType();
            MaintenanceSaveData data = FileStorageUtil.loadJson(getStoragePath(), type);

            if (data != null) {
                this.enabled = data.enabled;
                if (data.staffBypass != null) {
                    this.staffBypass.addAll(data.staffBypass);
                }
                this.customMotd = data.customMotd;
            } else {
                // Default to config value on first launch
                this.enabled = InStaffConfig.isMaintenanceEnabled();
                this.customMotd = InStaffConfig.getMaintenanceMotd();
            }
            InStaff.LOGGER.info("Maintenance status loaded: enabled={}, staffBypassCount={}", enabled, staffBypass.size());
        }
    }

    public void save() {
        synchronized (lock) {
            MaintenanceSaveData data = new MaintenanceSaveData();
            data.enabled = this.enabled;
            data.staffBypass = new HashSet<>(this.staffBypass);
            data.customMotd = this.customMotd;
            FileStorageUtil.saveAtomicJson(getStoragePath(), data);
        }
    }

    public boolean isMaintenance() {
        synchronized (lock) {
            return enabled;
        }
    }

    public void setMaintenance(boolean enabled) {
        synchronized (lock) {
            this.enabled = enabled;
            save();
        }
    }

    /**
     * Checks if a connecting player is authorized to join during maintenance.
     *
     * @param playerUUID Player's UUID
     * @param isOp       Whether the player has staff/OP permissions (level >= 2)
     * @return true if allowed to join, false if connection should be refused
     */
    public boolean isAllowed(@NotNull UUID playerUUID, boolean isOp) {
        synchronized (lock) {
            if (!enabled) {
                return true;
            }
            if (isOp) {
                return true;
            }
            return staffBypass.contains(playerUUID);
        }
    }

    public boolean addStaffBypass(@NotNull UUID uuid) {
        synchronized (lock) {
            boolean added = staffBypass.add(uuid);
            if (added) {
                save();
            }
            return added;
        }
    }

    public boolean removeStaffBypass(@NotNull UUID uuid) {
        synchronized (lock) {
            boolean removed = staffBypass.remove(uuid);
            if (removed) {
                save();
            }
            return removed;
        }
    }

    public boolean isStaffBypass(@NotNull UUID uuid) {
        synchronized (lock) {
            return staffBypass.contains(uuid);
        }
    }

    @NotNull
    public Set<UUID> getStaffBypassList() {
        synchronized (lock) {
            return Collections.unmodifiableSet(new HashSet<>(staffBypass));
        }
    }

    @NotNull
    public String getMotd() {
        synchronized (lock) {
            if (customMotd != null && !customMotd.isBlank()) {
                return customMotd;
            }
            return InStaffConfig.getMaintenanceMotd();
        }
    }

    public void setCustomMotd(@Nullable String motd) {
        synchronized (lock) {
            this.customMotd = motd;
            save();
        }
    }
}
