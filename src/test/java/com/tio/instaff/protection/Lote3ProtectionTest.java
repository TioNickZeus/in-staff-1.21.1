package com.tio.instaff.protection;

import com.tio.instaff.util.FileStorageUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Lote 3: Protection, Ban-Item and Event Handlers Tests")
class Lote3ProtectionTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setup() {
        FileStorageUtil.setDataDirectoryForTesting(tempDir);
    }

    @AfterAll
    static void tearDown() {
        FileStorageUtil.setDataDirectoryForTesting(null);
    }

    @Nested
    @DisplayName("BanItemMode Rules Tests")
    class ModeTests {
        @Test
        @DisplayName("BanItemMode flags correctly identify restrictions")
        void testBanItemModes() {
            assertTrue(BanItemMode.TOTAL.blocksPossession());
            assertTrue(BanItemMode.TOTAL.blocksUse());
            assertTrue(BanItemMode.TOTAL.blocksPlacement());

            assertFalse(BanItemMode.NO_USE.blocksPossession());
            assertTrue(BanItemMode.NO_USE.blocksUse());
            assertFalse(BanItemMode.NO_USE.blocksPlacement());

            assertFalse(BanItemMode.NO_PLACE.blocksPossession());
            assertFalse(BanItemMode.NO_PLACE.blocksUse());
            assertTrue(BanItemMode.NO_PLACE.blocksPlacement());
        }
    }

    @Nested
    @DisplayName("BanItemManager Operations Tests")
    class BanItemManagerTests {
        private final BanItemManager manager = BanItemManager.getInstance();

        @Test
        @DisplayName("Add, retrieve and remove banned items")
        void testBanItemOperations() {
            String tnt = "minecraft:tnt";
            String bedrock = "minecraft:bedrock";

            manager.banItem(tnt, BanItemMode.NO_PLACE);
            manager.banItem(bedrock, BanItemMode.TOTAL);

            assertEquals(BanItemMode.NO_PLACE, manager.getMode(tnt));
            assertEquals(BanItemMode.TOTAL, manager.getMode(bedrock));
            assertNull(manager.getMode("minecraft:dirt"));

            // Defensive null handling
            assertNull(manager.getMode((String) null));
            assertNull(manager.getMode(""));

            // Unban
            assertTrue(manager.unbanItem(tnt));
            assertNull(manager.getMode(tnt));
            assertFalse(manager.unbanItem("minecraft:dirt"));
        }
    }

    @Nested
    @DisplayName("ChunkQuarantineHandler Defensive Shield Tests")
    class QuarantineTests {
        @Test
        @DisplayName("Null safety and zero crash guarantee")
        void testQuarantineNullSafety() {
            assertDoesNotThrow(() -> assertFalse(ChunkQuarantineHandler.quarantineEntity(null, null)));
            assertDoesNotThrow(() -> assertFalse(ChunkQuarantineHandler.quarantineBlockEntity(null, null)));
            assertDoesNotThrow(() -> assertFalse(ChunkQuarantineHandler.quarantineEntity(null, new RuntimeException("Test crash"))));
        }

        @Test
        @DisplayName("Quarantine log path exists and is accessible")
        void testQuarantineLog() {
            Path logDir = FileStorageUtil.getDataDirectory();
            assertNotNull(logDir);
            assertTrue(Files.exists(logDir));
        }
    }
}
