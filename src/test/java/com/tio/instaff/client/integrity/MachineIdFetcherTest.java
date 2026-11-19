package com.tio.instaff.client.integrity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MachineIdFetcher Hybrid Token Derivation & Native Fingerprint Tests")
class MachineIdFetcherTest {

    @Test
    @DisplayName("TC-01: Stable deterministic SHA-256 token derivation for identical serverKey and fingerprint")
    void testDeterministicTokenGeneration() {
        String serverKey = "A9F3K2L8P1";
        String fingerprint = "cpu=Intel64|arch=AMD64|osArch=amd64|cores=16|ram=34359738368|disk=1000204886016|os=Windows 11|user=Player";

        String token1 = MachineIdFetcher.deriveToken(serverKey, fingerprint);
        String token2 = MachineIdFetcher.deriveToken(serverKey, fingerprint);

        assertNotNull(token1);
        assertNotNull(token2);
        assertEquals(token1, token2, "Token derivation must be strictly deterministic for the same serverKey and fingerprint");
    }

    @Test
    @DisplayName("TC-02: Cross-server isolation - distinct serverKeys yield distinct tokens for the same machine")
    void testCrossServerIsolation() {
        String fingerprint = "cpu=Intel64|arch=AMD64|osArch=amd64|cores=8|ram=17179869184|disk=500107862016|os=Linux|user=player";

        String tokenServerA = MachineIdFetcher.deriveToken("A9F3K2L8P1", fingerprint);
        String tokenServerB = MachineIdFetcher.deriveToken("Z1X2C3V4B5", fingerprint);

        assertNotEquals(tokenServerA, tokenServerB,
                "Distinct servers with different serverKeys must derive distinct client tokens for privacy");
    }

    @Test
    @DisplayName("TC-03: Cross-machine isolation - distinct fingerprints yield distinct tokens for the same serverKey")
    void testCrossMachineIsolation() {
        String serverKey = "A9F3K2L8P1";
        String fpA = "cpu=CPU_A|arch=AMD64|cores=8|ram=17179869184";
        String fpB = "cpu=CPU_B|arch=AMD64|cores=16|ram=34359738368";

        String tokenA = MachineIdFetcher.deriveToken(serverKey, fpA);
        String tokenB = MachineIdFetcher.deriveToken(serverKey, fpB);

        assertNotEquals(tokenA, tokenB, "Distinct machine fingerprints must derive completely different tokens");
    }

    @Test
    @DisplayName("TC-04: Format conformity - derived token is a valid 36-character lowercase UUID")
    void testTokenFormatConformity() {
        String token = MachineIdFetcher.deriveToken("KEY1234567", "cpu=test|cores=4");

        assertNotNull(token);
        assertEquals(36, token.length(), "Token must be exactly 36 characters");
        assertDoesNotThrow(() -> UUID.fromString(token), "Token must parse cleanly as a standard UUID");
        assertEquals(token, UUID.fromString(token).toString(), "Token must match standard lowercase UUID representation");
    }

    @Test
    @DisplayName("TC-05: Graceful fallback for null or blank serverKey or fingerprint without exceptions")
    void testNullAndEmptyFallback() {
        String tokenNullKey = MachineIdFetcher.deriveToken(null, "cpu=test|cores=4");
        String tokenBlankKey = MachineIdFetcher.deriveToken("   ", "cpu=test|cores=4");
        String tokenNullFp = MachineIdFetcher.deriveToken("A9F3K2L8P1", null);
        String tokenEmptyFp = MachineIdFetcher.deriveToken("A9F3K2L8P1", "");

        assertDoesNotThrow(() -> UUID.fromString(tokenNullKey));
        assertDoesNotThrow(() -> UUID.fromString(tokenBlankKey));
        assertDoesNotThrow(() -> UUID.fromString(tokenNullFp));
        assertDoesNotThrow(() -> UUID.fromString(tokenEmptyFp));

        assertNotEquals(tokenNullKey, tokenBlankKey, "Null and blank fallbacks should produce independent random UUIDs");
        assertNotEquals(tokenNullFp, tokenEmptyFp, "Null and empty fingerprint fallbacks should produce independent random UUIDs");
    }

    @Test
    @DisplayName("TC-06: Native fingerprint collection is non-blank, deterministic, and safe in headless test runtime")
    void testNativeFingerprintCollection() {
        String fp1 = assertDoesNotThrow(MachineIdFetcher::collectNativeFingerprint);
        String fp2 = assertDoesNotThrow(MachineIdFetcher::collectNativeFingerprint);

        assertNotNull(fp1);
        assertFalse(fp1.isBlank(), "Collected native fingerprint must never be blank");
        assertEquals(fp1, fp2, "In-memory native fingerprint collection must remain deterministic across calls");
    }

    @Test
    @DisplayName("TC-07: Sanitization - generateClientToken hashes signals one-way without leaking raw machine data")
    void testSensitiveSignalSanitization() {
        String sensitiveMarker = "SENSITIVE-HOST-MARKER-998877";
        String tokenFromSensitive = MachineIdFetcher.deriveToken("A9F3K2L8P1", sensitiveMarker);

        assertEquals(36, tokenFromSensitive.length());
        assertFalse(tokenFromSensitive.contains("SENSITIVE"), "Derived token must never contain raw fingerprint fragments");
        assertDoesNotThrow(() -> UUID.fromString(tokenFromSensitive));

        String generated1 = MachineIdFetcher.generateClientToken("A9F3K2L8P1");
        String generated2 = MachineIdFetcher.generateClientToken("A9F3K2L8P1");
        String generatedOtherServer = MachineIdFetcher.generateClientToken("B2G4H6J8K0");

        assertEquals(generated1, generated2, "generateClientToken must be deterministic for the same serverKey");
        assertNotEquals(generated1, generatedOtherServer, "generateClientToken must isolate tokens across different serverKeys");
    }
}
