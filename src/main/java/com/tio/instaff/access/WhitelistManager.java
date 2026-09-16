package com.tio.instaff.access;

import com.google.gson.reflect.TypeToken;
import com.tio.instaff.InStaff;
import com.tio.instaff.util.FileStorageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread-safe smart whitelist manager for In-Staff.
 * Operates independently of the vanilla whitelist, supporting on-the-fly reload
 * and local offline-mode UUID compatibility.
 */
public final class WhitelistManager {

    private static final WhitelistManager INSTANCE = new WhitelistManager();
    private static final String FILE_NAME = "whitelist.json";

    private final Object lock = new Object();
    private final Object ioLock = new Object();
    private boolean enabled;
    private final Map<UUID, String> allowedPlayers = new HashMap<>();

    private static class WhitelistSaveData {
        boolean enabled;
        Map<UUID, String> allowedPlayers = new HashMap<>();
    }

    private WhitelistManager() {
        load();
    }

    public static WhitelistManager getInstance() {
        return INSTANCE;
    }

    private Path getStoragePath() {
        return FileStorageUtil.getDataDirectory().resolve(FILE_NAME);
    }

    public void load() {
        synchronized (lock) {
            allowedPlayers.clear();
            Type type = new TypeToken<WhitelistSaveData>() {}.getType();
            WhitelistSaveData data = FileStorageUtil.loadJson(getStoragePath(), type);

            if (data != null) {
                this.enabled = data.enabled;
                if (data.allowedPlayers != null) {
                    this.allowedPlayers.putAll(data.allowedPlayers);
                }
            } else {
                this.enabled = false;
            }
            InStaff.LOGGER.info("Smart Whitelist loaded: enabled={}, totalPlayers={}", enabled, allowedPlayers.size());
        }
    }

    public void save() {
        WhitelistSaveData data = new WhitelistSaveData();
        synchronized (lock) {
            data.enabled = this.enabled;
            data.allowedPlayers = new HashMap<>(this.allowedPlayers);
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            synchronized (ioLock) {
                FileStorageUtil.saveAtomicJson(getStoragePath(), data);
            }
        });
    }

    public boolean isEnabled() {
        synchronized (lock) {
            return enabled;
        }
    }

    public void setEnabled(boolean enabled) {
        synchronized (lock) {
            this.enabled = enabled;
            save();
        }
    }

    public boolean isWhitelisted(@NotNull UUID uuid) {
        synchronized (lock) {
            if (!enabled) {
                return true;
            }
            return allowedPlayers.containsKey(uuid);
        }
    }

    public boolean addPlayer(@NotNull UUID uuid, @NotNull String name) {
        synchronized (lock) {
            boolean wasPresent = allowedPlayers.containsKey(uuid);
            allowedPlayers.put(uuid, name);
            save();
            return !wasPresent;
        }
    }

    public boolean removePlayer(@NotNull UUID uuid) {
        synchronized (lock) {
            boolean removed = allowedPlayers.remove(uuid) != null;
            if (removed) {
                save();
            }
            return removed;
        }
    }

    @NotNull
    public Map<UUID, String> getAllowedPlayers() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new HashMap<>(allowedPlayers));
        }
    }

    public void reload() {
        load();
    }
}
