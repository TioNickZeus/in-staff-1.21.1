package com.tio.instaff.access;

import com.tio.instaff.util.FileStorageUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientTokenManager Persistence & ServerKey Tests")
class ClientTokenManagerTest {

    private static final Pattern SERVER_KEY_PATTERN = Pattern.compile("^[A-Z0-9]{10}$");

    @TempDir
    static Path tempDir;

    private ClientTokenManager manager;

    @BeforeAll
    static void beforeAll() {
        FileStorageUtil.setDataDirectoryForTesting(tempDir);
    }

    @AfterAll
    static void afterAll() {
        FileStorageUtil.setDataDirectoryForTesting(null);
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(tempDir.resolve("client_tokens.json"));
        manager = ClientTokenManager.getInstance();
        manager.resetForTesting();
        manager.load();
    }

    @AfterEach
    void tearDown() throws IOException {
        manager.resetForTesting();
        Files.deleteIfExists(tempDir.resolve("client_tokens.json"));
    }

    @Test
    @DisplayName("TC-01: Initialization generates a 10-char uppercase alphanumeric serverKey and persists client_tokens.json")
    void testServerKeyGenerationAndPersistence() {
        String key = manager.getServerKey();
        Path dbFile = tempDir.resolve("client_tokens.json");

        assertNotNull(key);
        assertTrue(SERVER_KEY_PATTERN.matcher(key).matches(),
                "serverKey must be 10 uppercase alphanumeric characters, got: " + key);
        assertTrue(Files.exists(dbFile), "client_tokens.json must be persisted immediately on boot");
    }

    @Test
    @DisplayName("TC-02: load() reloads existing serverKey and player token records from disk")
    void testReloadPersistedServerKeyAndRecords() {
        String initialKey = manager.getServerKey();
        UUID playerUuid = UUID.randomUUID();
        String clientToken = UUID.randomUUID().toString();

        manager.recordPlayerToken(playerUuid, "StaffTester", clientToken);

        manager.resetForTesting();
        manager.load();

        assertEquals(initialKey, manager.getServerKey(), "Reloaded serverKey must match persisted key");
        assertEquals(clientToken, manager.findTokenByPlayer(playerUuid), "Reloaded player token must match");

        Optional<ClientTokenManager.PlayerTokenRecord> record = manager.getRecord(playerUuid);
        assertTrue(record.isPresent());
        assertEquals("StaffTester", record.get().getPlayerName());
        assertEquals(clientToken, record.get().getClientToken());
    }

    @Test
    @DisplayName("TC-03: recordPlayerToken skips redundant disk write when token and name are unchanged, and writes on change")
    void testRecordPlayerTokenWriteDeduplication() throws IOException {
        UUID playerUuid = UUID.randomUUID();
        String tokenA = UUID.randomUUID().toString();
        String tokenB = UUID.randomUUID().toString();
        Path dbFile = tempDir.resolve("client_tokens.json");

        manager.recordPlayerToken(playerUuid, "PlayerOne", tokenA);
        assertEquals(tokenA, manager.findTokenByPlayer(playerUuid));

        // Pin file timestamp to detect whether subsequent calls perform disk I/O
        FileTime pinnedTime = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(dbFile, pinnedTime);

        // Identical token and name -> should update lastSeenEpoch in memory without rewriting disk
        manager.recordPlayerToken(playerUuid, "PlayerOne", tokenA);
        assertEquals(pinnedTime, Files.getLastModifiedTime(dbFile),
                "Unchanged token and playerName should avoid redundant disk writes");

        // Changed token -> must persist to disk
        manager.recordPlayerToken(playerUuid, "PlayerOne", tokenB);
        assertEquals(tokenB, manager.findTokenByPlayer(playerUuid));
        assertNotEquals(pinnedTime, Files.getLastModifiedTime(dbFile),
                "Changed clientToken must trigger persistence to disk");
    }

    @Test
    @DisplayName("TC-04: Blank or null tokens are ignored and unknown players return null")
    void testNullAndBlankTokenHandling() {
        UUID playerUuid = UUID.randomUUID();

        manager.recordPlayerToken(playerUuid, "PlayerOne", null);
        manager.recordPlayerToken(playerUuid, "PlayerOne", "   ");

        assertNull(manager.findTokenByPlayer(playerUuid));
        assertNull(manager.findTokenByPlayer(null));
    }

    @Test
    @DisplayName("TC-05: Missing or invalid serverKey on disk regenerates a valid key while preserving existing tokens")
    void testInvalidServerKeyRecoveryPreservesTokens() throws IOException {
        UUID playerUuid = UUID.randomUUID();
        String existingToken = UUID.randomUUID().toString();
        String rawJson = """
                {
                  "serverKey": "",
                  "tokens": [
                    {
                      "playerUUID": "%s",
                      "playerName": "RecoveredPlayer",
                      "clientToken": "%s",
                      "lastSeenEpoch": 1759680000000
                    }
                  ]
                }
                """.formatted(playerUuid, existingToken);
        Files.writeString(tempDir.resolve("client_tokens.json"), rawJson);

        manager.load();

        String regeneratedKey = manager.getServerKey();
        assertTrue(SERVER_KEY_PATTERN.matcher(regeneratedKey).matches(),
                "Invalid serverKey must be replaced with a valid 10-char alphanumeric key");
        assertEquals(existingToken, manager.findTokenByPlayer(playerUuid),
                "Existing player token records must be preserved when regenerating serverKey");
    }
}
