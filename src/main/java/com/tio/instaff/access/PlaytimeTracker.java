package com.tio.instaff.access;

import com.google.gson.reflect.TypeToken;
import com.tio.instaff.InStaff;
import com.tio.instaff.util.DurationParser;
import com.tio.instaff.util.FileStorageUtil;
import net.minecraft.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe playtime and activity tracker for In-Staff.
 * Strictly uses net.minecraft.Util.getMillis() for monotonic session timing,
 * persisting accumulated session durations and telemetry to instaff/playtime.json.
 */
public final class PlaytimeTracker {

    private static final PlaytimeTracker INSTANCE = new PlaytimeTracker();
    private static final String FILE_NAME = "playtime.json";

    private final Object lock = new Object();
    private final Object ioLock = new Object();
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, PlaytimeData> dataStore = new ConcurrentHashMap<>();

    public static class PlaytimeData {
        private UUID uuid;
        private String lastKnownName;
        private long totalPlaytimeMillis;
        private long firstSeenEpoch;
        private long lastSeenEpoch;

        public PlaytimeData() {
        }

        public PlaytimeData(UUID uuid, String name) {
            this.uuid = uuid;
            this.lastKnownName = name;
            this.totalPlaytimeMillis = 0L;
            this.firstSeenEpoch = System.currentTimeMillis();
            this.lastSeenEpoch = this.firstSeenEpoch;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getLastKnownName() {
            return lastKnownName != null ? lastKnownName : "Unknown";
        }

        public long getTotalPlaytimeMillis() {
            return totalPlaytimeMillis;
        }

        public long getFirstSeenEpoch() {
            return firstSeenEpoch;
        }

        public long getLastSeenEpoch() {
            return lastSeenEpoch;
        }
    }

    private PlaytimeTracker() {
        load();
    }

    public static PlaytimeTracker getInstance() {
        return INSTANCE;
    }

    private Path getStoragePath() {
        return FileStorageUtil.getDataDirectory().resolve(FILE_NAME);
    }

    public void load() {
        synchronized (lock) {
            dataStore.clear();
            Type mapType = new TypeToken<Map<UUID, PlaytimeData>>() {}.getType();
            Map<UUID, PlaytimeData> loaded = FileStorageUtil.loadJson(getStoragePath(), mapType);

            if (loaded != null) {
                dataStore.putAll(loaded);
            }
            InStaff.LOGGER.info("Playtime records loaded: {} players tracked.", dataStore.size());
        }
    }

    public void save() {
        Map<UUID, PlaytimeData> snapshot;
        synchronized (lock) {
            snapshot = new HashMap<>(dataStore);
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            synchronized (ioLock) {
                FileStorageUtil.saveAtomicJson(getStoragePath(), snapshot);
            }
        });
    }

    /**
     * Starts tracking monotonic playtime for a joined player.
     */
    public void onPlayerJoin(@NotNull UUID uuid, @NotNull String name) {
        sessionStartTimes.put(uuid, Util.getMillis());

        synchronized (lock) {
            PlaytimeData data = dataStore.computeIfAbsent(uuid, k -> new PlaytimeData(uuid, name));
            data.lastKnownName = name;
            data.lastSeenEpoch = System.currentTimeMillis();
            save();
        }
    }

    /**
     * Accumulates elapsed monotonic session playtime and updates last seen timestamp on leave.
     */
    public void onPlayerLeave(@NotNull UUID uuid) {
        Long sessionStart = sessionStartTimes.remove(uuid);
        if (sessionStart != null) {
            long sessionDuration = Util.getMillis() - sessionStart;
            if (sessionDuration > 0) {
                synchronized (lock) {
                    PlaytimeData data = dataStore.get(uuid);
                    if (data != null) {
                        data.totalPlaytimeMillis += sessionDuration;
                        data.lastSeenEpoch = System.currentTimeMillis();
                        save();
                    }
                }
            }
        }
    }

    /**
     * Gets the total accumulated playtime in milliseconds, including live session time if online.
     */
    public long getTotalPlaytimeMillis(@NotNull UUID uuid) {
        PlaytimeData data = dataStore.get(uuid);
        long accumulated = (data != null) ? data.totalPlaytimeMillis : 0L;

        Long sessionStart = sessionStartTimes.get(uuid);
        if (sessionStart != null) {
            long liveSession = Util.getMillis() - sessionStart;
            if (liveSession > 0) {
                accumulated += liveSession;
            }
        }

        return accumulated;
    }

    /**
     * Returns a human-readable formatted string of total playtime (e.g. "2d 4h 15m").
     */
    @NotNull
    public String getFormattedPlaytime(@NotNull UUID uuid) {
        return DurationParser.formatDuration(getTotalPlaytimeMillis(uuid));
    }

    public long getFirstSeenEpoch(@NotNull UUID uuid) {
        PlaytimeData data = dataStore.get(uuid);
        return (data != null) ? data.firstSeenEpoch : 0L;
    }

    public long getLastSeenEpoch(@NotNull UUID uuid) {
        if (isOnline(uuid)) {
            return System.currentTimeMillis();
        }
        PlaytimeData data = dataStore.get(uuid);
        return (data != null) ? data.lastSeenEpoch : 0L;
    }

    public boolean isOnline(@NotNull UUID uuid) {
        return sessionStartTimes.containsKey(uuid);
    }

    @Nullable
    public PlaytimeData getPlaytimeData(@NotNull UUID uuid) {
        return dataStore.get(uuid);
    }

    @NotNull
    public Map<UUID, PlaytimeData> getAllData() {
        return Collections.unmodifiableMap(new HashMap<>(dataStore));
    }
}
