package com.tio.instaff.client.integrity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MachineIdFetcher & Token Derivation Tests")
class MachineIdFetcherTest {

    @Test
    @DisplayName("TC-01: Stable deterministic SHA-256 token derivation for identical hardware IDs")
    void testDeterministicTokenGeneration() {
        String rawHardwareId = "550e8400-e29b-41d4-a716-446655440000";

        String token1 = MachineIdFetcher.toSha256Token(rawHardwareId);
        String token2 = MachineIdFetcher.toSha256Token(rawHardwareId);

        assertNotNull(token1);
        assertNotNull(token2);
        assertEquals(token1, token2, "Token derivation must be strictly deterministic for the same hardware ID");
    }

    @Test
    @DisplayName("TC-02: Distinct hardware identifiers produce distinct tokens (Machine HWID isolation)")
    void testDistinctHardwareGeneratesDistinctTokens() {
        String hwidA = "11111111-2222-3333-4444-555555555555";
        String hwidB = "99999999-8888-7777-6666-555555555555";

        String tokenA = MachineIdFetcher.toSha256Token(hwidA);
        String tokenB = MachineIdFetcher.toSha256Token(hwidB);

        assertNotEquals(tokenA, tokenB, "Distinct hardware IDs must derive completely different tokens");
    }

    @Test
    @DisplayName("TC-03: Format conformity - derived token is a valid 36-character UUID")
    void testTokenFormatConformity() {
        String rawId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String token = MachineIdFetcher.toSha256Token(rawId);

        assertNotNull(token);
        assertEquals(36, token.length(), "Token must be exactly 36 characters");
        assertDoesNotThrow(() -> UUID.fromString(token), "Token must parse cleanly as a standard UUID");
        assertEquals(token, UUID.fromString(token).toString(), "Token must match standard lowercase UUID representation");
    }

    @Test
    @DisplayName("TC-04: Graceful fallback for null or empty input without exceptions")
    void testNullAndEmptyFallback() {
        String tokenFromNull = MachineIdFetcher.toSha256Token(null);
        String tokenFromEmpty = MachineIdFetcher.toSha256Token("");
        String tokenFromWhitespace = MachineIdFetcher.toSha256Token("   \t\n  ");

        assertNotNull(tokenFromNull);
        assertNotNull(tokenFromEmpty);
        assertNotNull(tokenFromWhitespace);

        assertDoesNotThrow(() -> UUID.fromString(tokenFromNull));
        assertDoesNotThrow(() -> UUID.fromString(tokenFromEmpty));
        assertDoesNotThrow(() -> UUID.fromString(tokenFromWhitespace));

        assertNotEquals(tokenFromNull, tokenFromEmpty, "Null and empty fallbacks should produce independent random UUIDs");
    }

    @Test
    @DisplayName("TC-05: OS Output Extraction - Windows reg query output parsing")
    void testWindowsRegQueryExtraction() {
        String sampleWindowsOutput = "\r\n" +
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography\r\n" +
                "    MachineGuid    REG_SZ    d2b82e25-1e34-4b55-a4b5-6f1c7f998811\r\n" +
                "\r\n";

        Optional<String> extracted = MachineIdFetcher.extractUuidFromWindowsOutput(sampleWindowsOutput);
        assertTrue(extracted.isPresent());
        assertEquals("d2b82e25-1e34-4b55-a4b5-6f1c7f998811", extracted.get());

        // Malformed or empty Windows output
        assertTrue(MachineIdFetcher.extractUuidFromWindowsOutput("ERROR: The system was unable to find the specified registry key or value.").isEmpty());
        assertTrue(MachineIdFetcher.extractUuidFromWindowsOutput(null).isEmpty());
        assertTrue(MachineIdFetcher.extractUuidFromWindowsOutput("").isEmpty());
    }

    @Test
    @DisplayName("TC-05: OS Output Extraction - Linux /etc/machine-id parsing")
    void testLinuxMachineIdExtraction() {
        String sample32Hex = "b925b3901b0f4cfd911b02d847167512\n";
        Optional<String> extractedHex = MachineIdFetcher.sanitizeLinuxMachineId(sample32Hex);
        assertTrue(extractedHex.isPresent());
        assertEquals("b925b3901b0f4cfd911b02d847167512", extractedHex.get());

        String sampleUuid = "b925b390-1b0f-4cfd-911b-02d847167512\n";
        Optional<String> extractedUuid = MachineIdFetcher.sanitizeLinuxMachineId(sampleUuid);
        assertTrue(extractedUuid.isPresent());
        assertEquals("b925b390-1b0f-4cfd-911b-02d847167512", extractedUuid.get());

        // Invalid Linux machine ID
        assertTrue(MachineIdFetcher.sanitizeLinuxMachineId("too-short").isEmpty());
        assertTrue(MachineIdFetcher.sanitizeLinuxMachineId(null).isEmpty());
        assertTrue(MachineIdFetcher.sanitizeLinuxMachineId("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz").isEmpty());
    }

    @Test
    @DisplayName("TC-05: OS Output Extraction - macOS ioreg output parsing")
    void testMacIoregExtraction() {
        String sampleMacOutput = "  |   \"IOPlatformSerialNumber\" = \"C02ABCDEFGH1\"\n" +
                "  |   \"IOPlatformUUID\" = \"4A582F72-3580-5B92-9C45-A6724E62F93C\"\n" +
                "  |   \"clock-frequency\" = <00676980>\n";

        Optional<String> extracted = MachineIdFetcher.extractUuidFromMacOutput(sampleMacOutput);
        assertTrue(extracted.isPresent());
        assertEquals("4A582F72-3580-5B92-9C45-A6724E62F93C", extracted.get());

        // Malformed macOS output
        assertTrue(MachineIdFetcher.extractUuidFromMacOutput("No matching property found").isEmpty());
        assertTrue(MachineIdFetcher.extractUuidFromMacOutput(null).isEmpty());
        assertTrue(MachineIdFetcher.extractUuidFromMacOutput("").isEmpty());
    }

    @Test
    @DisplayName("TC-08: Exception & Sensitive Data Sanitization - Raw hardware string never leaked")
    void testSensitiveHardwareSanitization() {
        String sensitiveString = "SENSITIVE-RAW-HWID-12345-SECRET";

        // Feed sensitive invalid format to parsers and verify no exception leaks it
        Optional<String> winResult = MachineIdFetcher.extractUuidFromWindowsOutput("Error output with " + sensitiveString);
        assertTrue(winResult.isEmpty());

        Optional<String> linuxResult = MachineIdFetcher.sanitizeLinuxMachineId(sensitiveString);
        assertTrue(linuxResult.isEmpty());

        Optional<String> macResult = MachineIdFetcher.extractUuidFromMacOutput("Random line with " + sensitiveString);
        assertTrue(macResult.isEmpty());

        // Verify that fetchRawMachineIdentifier does not throw any unhandled exceptions on current host
        assertDoesNotThrow(MachineIdFetcher::fetchRawMachineIdentifier);
    }
}
