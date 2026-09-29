package com.tio.instaff.access;

/**
 * Terminal evaluation outcomes for device lock validation during login and integrity handshake.
 */
public enum DeviceValidationResult {
    /**
     * Account is not staff or device lock feature is disabled globally.
     */
    NOT_APPLICABLE,

    /**
     * The device locks database failed to load or is corrupted; fail-closed safety active.
     */
    CORRUPTED,

    /**
     * Staff account has no binding registered yet; permitted to proceed without quarantine.
     */
    NO_BINDING,

    /**
     * Binding exists and the presented client installation token matches the registered token.
     */
    MATCH,

    /**
     * Binding exists but the presented client installation token differs (potential impersonation).
     */
    MISMATCH
}
