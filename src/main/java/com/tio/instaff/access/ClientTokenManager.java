package com.tio.instaff.access;

import com.tio.instaff.InStaff;
import com.tio.instaff.util.FileStorageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for the 50% server key and persistent player client tokens.
 * Manages <server_root>/instaff/client_tokens.json using atomic file swaps.
 */
public final class ClientTokenManager {

    private static final String FILE_NAME = "client_tokens.json";
    private static final String KEY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SERVER_KEY_LENGTH = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ClientTokenManager INSTANCE = new ClientTokenManager();

    private final Object lock = new Object();
    private final Object ioLock = new Object();
    private final Map<UUID, PlayerTokenRecord> tokenRecords = new ConcurrentHashMap<>();
    private volatile String serverKey = "";

    private ClientTokenManager() {
        load();
    }

    public static ClientTokenManager getInstance() {
        return INSTANCE;
    }

    private Path getStoragePath() {
        return FileStorageUtil.getDataDirectory().resolve(FILE_NAME);
    }

    /**
     * Loads serverKey and player token records from client_tokens.json.
     * If the file is missing, unreadable, or lacks a valid 10-char serverKey,
     * generates a new key via SecureRandom and persists it immediately.
     */
    public void load() {
        synchronized (lock) {
            tokenRecords.clear();
            Path storagePath = getStoragePath();

            if (!Files.exists(storagePath)) {
                this.serverKey = generateRandomServerKey();
                saveSync();
                InStaff.LOGGER.info("Generated new serverKey and initialized {}", FILE_NAME);
                return;
            }

            ClientTokenDatabase loaded = FileStorageUtil.loadJson(storagePath, ClientTokenDatabase.class);
            boolean needsSave = false;

            if (loaded != null && loaded.getTokens() != null) {
                for (PlayerTokenRecord record : loaded.getTokens()) {
                    if (record != null
                            && record.getPlayerUUID() != null
                            && record.getClientToken() != null
                            && !record.getClientToken().isBlank()) {
                        tokenRecords.put(record.getPlayerUUID(), record);
                    }
                }
            }

            if (loaded == null || !isValidServerKey(loaded.getServerKey())) {
                this.serverKey = generateRandomServerKey();
                needsSave = true;
                InStaff.LOGGER.warn("Missing or invalid serverKey in {}. Generated a fresh serverKey.", FILE_NAME);
            } else {
                this.serverKey = loaded.getServerKey();
            }

            if (needsSave) {
                saveSync();
            }

            InStaff.LOGGER.info("Loaded {} client token records from {}.", tokenRecords.size(), FILE_NAME);
        }
    }

    /**
     * Atomically saves the current serverKey and token records asynchronously.
     */
    public void save() {
        ClientTokenDatabase snapshot = buildSnapshot();
        CompletableFuture.runAsync(() -> {
            synchronized (ioLock) {
                FileStorageUtil.saveAtomicJson(getStoragePath(), snapshot);
            }
        });
    }

    /**
     * Atomically saves the current serverKey and token records synchronously.
     */
    public void saveSync() {
        ClientTokenDatabase snapshot = buildSnapshot();
        synchronized (ioLock) {
            FileStorageUtil.saveAtomicJson(getStoragePath(), snapshot);
        }
    }

    private ClientTokenDatabase buildSnapshot() {
        synchronized (lock) {
            return new ClientTokenDatabase(this.serverKey, new ArrayList<>(tokenRecords.values()));
        }
    }

    /**
     * Returns the persistent 10-character alphanumeric serverKey, generating and persisting
     * one if not yet initialized.
     */
    @NotNull
    public String getServerKey() {
        String current = this.serverKey;
        if (current == null || current.isBlank()) {
            synchronized (lock) {
                if (this.serverKey == null || this.serverKey.isBlank()) {
                    this.serverKey = generateRandomServerKey();
                    saveSync();
                }
                current = this.serverKey;
            }
        }
        return current;
    }

    /**
     * Records or updates a player's presented client token.
     * Avoids redundant disk writes when both clientToken and playerName are unchanged.
     */
    public void recordPlayerToken(@NotNull UUID playerUUID, @Nullable String playerName, @Nullable String clientToken) {
        if (clientToken == null || clientToken.isBlank()) {
            return;
        }

        String safeName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        String safeToken = clientToken.trim();
        long now = System.currentTimeMillis();

        synchronized (lock) {
            if (this.serverKey == null || this.serverKey.isBlank()) {
                this.serverKey = generateRandomServerKey();
            }

            PlayerTokenRecord existing = tokenRecords.get(playerUUID);
            if (existing != null
                    && safeToken.equals(existing.getClientToken())
                    && safeName.equals(existing.getPlayerName())) {
                existing.setLastSeenEpoch(now);
                return;
            }

            PlayerTokenRecord record = existing != null
                    ? existing
                    : new PlayerTokenRecord(playerUUID, safeName, safeToken, now);
            record.setPlayerName(safeName);
            record.setClientToken(safeToken);
            record.setLastSeenEpoch(now);
            tokenRecords.put(playerUUID, record);
            saveSync();
        }
    }

    /**
     * Looks up the last recorded client token for the given player UUID.
     */
    @Nullable
    public String findTokenByPlayer(@Nullable UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }
        PlayerTokenRecord record = tokenRecords.get(playerUUID);
        return record != null ? record.getClientToken() : null;
    }

    @NotNull
    public Optional<PlayerTokenRecord> getRecord(@NotNull UUID playerUUID) {
        return Optional.ofNullable(tokenRecords.get(playerUUID));
    }

    @NotNull
    public List<PlayerTokenRecord> listAll() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(tokenRecords.values()));
        }
    }

    /**
     * Resets in-memory state and regenerates a fresh serverKey without failing if the storage directory changes.
     */
    public void resetForTesting() {
        synchronized (lock) {
            tokenRecords.clear();
            this.serverKey = generateRandomServerKey();
        }
    }

    @NotNull
    private static String generateRandomServerKey() {
        StringBuilder sb = new StringBuilder(SERVER_KEY_LENGTH);
        for (int i = 0; i < SERVER_KEY_LENGTH; i++) {
            sb.append(KEY_ALPHABET.charAt(SECURE_RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static boolean isValidServerKey(@Nullable String key) {
        if (key == null || key.length() != SERVER_KEY_LENGTH) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean alphanumeric = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9');
            if (!alphanumeric) {
                return false;
            }
        }
        return true;
    }

    /**
     * JSON root object schema for instaff/client_tokens.json.
     */
    public static final class ClientTokenDatabase {
        private String serverKey;
        private List<PlayerTokenRecord> tokens = new ArrayList<>();

        public ClientTokenDatabase() {
        }

        public ClientTokenDatabase(@Nullable String serverKey, @Nullable List<PlayerTokenRecord> tokens) {
            this.serverKey = serverKey;
            this.tokens = tokens != null ? tokens : new ArrayList<>();
        }

        @Nullable
        public String getServerKey() {
            return serverKey;
        }

        public void setServerKey(@Nullable String serverKey) {
            this.serverKey = serverKey;
        }

        @NotNull
        public List<PlayerTokenRecord> getTokens() {
            if (tokens == null) {
                tokens = new ArrayList<>();
            }
            return tokens;
        }

        public void setTokens(@Nullable List<PlayerTokenRecord> tokens) {
            this.tokens = tokens != null ? tokens : new ArrayList<>();
        }
    }

    /**
     * Per-player token record structured for future SQL/MySQL migration.
     */
    public static final class PlayerTokenRecord {
        private UUID playerUUID;
        private String playerName;
        private String clientToken;
        private long lastSeenEpoch;

        public PlayerTokenRecord() {
        }

        public PlayerTokenRecord(@NotNull UUID playerUUID, @Nullable String playerName,
                                 @NotNull String clientToken, long lastSeenEpoch) {
            this.playerUUID = playerUUID;
            this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
            this.clientToken = clientToken;
            this.lastSeenEpoch = lastSeenEpoch;
        }

        @Nullable
        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public void setPlayerUUID(@Nullable UUID playerUUID) {
            this.playerUUID = playerUUID;
        }

        @NotNull
        public String getPlayerName() {
            return playerName != null ? playerName : "Unknown";
        }

        public void setPlayerName(@Nullable String playerName) {
            this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        }

        @NotNull
        public String getClientToken() {
            return clientToken != null ? clientToken : "";
        }

        public void setClientToken(@NotNull String clientToken) {
            this.clientToken = clientToken;
        }

        public long getLastSeenEpoch() {
            return lastSeenEpoch;
        }

        public void setLastSeenEpoch(long lastSeenEpoch) {
            this.lastSeenEpoch = lastSeenEpoch;
        }
    }
}
