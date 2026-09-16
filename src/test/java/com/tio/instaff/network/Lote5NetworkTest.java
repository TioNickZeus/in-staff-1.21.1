package com.tio.instaff.network;

import com.tio.instaff.client.integrity.ClientHashScanner;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Lote 5: Network Payloads and Client Integrity Tests")
class Lote5NetworkTest {

    @Nested
    @DisplayName("Payload Codec Serialization Tests")
    class PayloadCodecTests {
        @Test
        @DisplayName("IntegrityRequestPayload encode and decode matches exact fields")
        void testRequestPayloadCodec() {
            List<String> required = List.of("jei", "curios");
            List<String> blacklistedIds = List.of("xray", "baritone");
            List<String> blacklistedHashes = List.of("abc123hash", "def456hash");

            IntegrityRequestPayload original = new IntegrityRequestPayload(15, required, blacklistedIds, blacklistedHashes);

            ByteBuf buffer = Unpooled.buffer();
            RegistryFriendlyByteBuf friendlyBuf = new RegistryFriendlyByteBuf(buffer, null);

            IntegrityRequestPayload.STREAM_CODEC.encode(friendlyBuf, original);
            IntegrityRequestPayload decoded = IntegrityRequestPayload.STREAM_CODEC.decode(friendlyBuf);

            assertNotNull(decoded);
            assertEquals(15, decoded.timeoutSeconds());
            assertEquals(required, decoded.requiredMods());
            assertEquals(blacklistedIds, decoded.blacklistedModIds());
            assertEquals(blacklistedHashes, decoded.blacklistedHashes());
        }

        @Test
        @DisplayName("IntegrityResponsePayload encode and decode matches exact fields")
        void testResponsePayloadCodec() {
            String token = UUID.randomUUID().toString();
            Map<String, String> hashes = Map.of("modA.jar", "hashA", "modB.jar", "hashB");
            List<String> modIds = List.of("instaff", "minecraft", "neoforge");

            IntegrityResponsePayload original = new IntegrityResponsePayload(token, hashes, modIds);

            ByteBuf buffer = Unpooled.buffer();
            RegistryFriendlyByteBuf friendlyBuf = new RegistryFriendlyByteBuf(buffer, null);

            IntegrityResponsePayload.STREAM_CODEC.encode(friendlyBuf, original);
            IntegrityResponsePayload decoded = IntegrityResponsePayload.STREAM_CODEC.decode(friendlyBuf);

            assertNotNull(decoded);
            assertEquals(token, decoded.clientToken());
            assertEquals(hashes, decoded.modHashes());
            assertEquals(modIds, decoded.loadedModIds());
        }
    }

    @Nested
    @DisplayName("Payload Hardening Tests")
    class PayloadHardeningTests {

        /**
         * Regression guard: the element count came straight from an untrusted client and was
         * used to pre-size the collection, so a few-byte packet could request a multi-gigabyte
         * allocation and take the server down with an OutOfMemoryError.
         */
        @Test
        @DisplayName("Absurd element count is rejected instead of pre-allocating")
        void testHugeCountIsRejected() {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            buf.writeUtf("some-token");
            buf.writeVarInt(Integer.MAX_VALUE);

            assertThrows(DecoderException.class, () -> IntegrityResponsePayload.STREAM_CODEC.decode(buf));
        }

        @Test
        @DisplayName("Element count larger than the remaining bytes is rejected")
        void testCountLargerThanBufferIsRejected() {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            buf.writeUtf("some-token");
            buf.writeVarInt(64);

            assertThrows(DecoderException.class, () -> IntegrityResponsePayload.STREAM_CODEC.decode(buf));
        }

        @Test
        @DisplayName("Legitimate payloads still round-trip after the bounds checks")
        void testNormalPayloadStillDecodes() {
            IntegrityResponsePayload original = new IntegrityResponsePayload(
                    UUID.randomUUID().toString(),
                    Map.of("modA.jar", "hashA"),
                    List.of("instaff", "neoforge"));

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            IntegrityResponsePayload.STREAM_CODEC.encode(buf, original);

            assertEquals(original, IntegrityResponsePayload.STREAM_CODEC.decode(buf));
        }
    }

    @Nested
    @DisplayName("ClientHashScanner File Hashing Tests")
    class HashScannerTests {
        @Test
        @DisplayName("SHA-256 calculation matches known cryptographic digest")
        void testSha256(@TempDir Path tempDir) throws IOException {
            Path testFile = tempDir.resolve("test_sample.txt");
            Files.writeString(testFile, "hello world", StandardCharsets.UTF_8);

            String expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
            String computedHash = ClientHashScanner.computeFileSha256(testFile);

            assertEquals(expectedHash, computedHash);
        }

        @Test
        @DisplayName("Missing file returns empty string gracefully without throwing")
        void testMissingFileHash() {
            Path nonExistent = Path.of("missing_file_never_exists.jar");
            assertDoesNotThrow(() -> {
                String hash = ClientHashScanner.computeFileSha256(nonExistent);
                assertEquals("", hash);
            });
        }
    }

    @Nested
    @DisplayName("ServerIntegrityValidator State Tests")
    class ValidatorTests {
        private final ServerIntegrityValidator validator = ServerIntegrityValidator.getInstance();

        @Test
        @DisplayName("Player leave clears pending verifications")
        void testPlayerLeave() {
            UUID testUUID = UUID.randomUUID();
            assertDoesNotThrow(() -> validator.onPlayerLeave(testUUID));
        }
    }
}
