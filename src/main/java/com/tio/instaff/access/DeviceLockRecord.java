package com.tio.instaff.access;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistent record linking a staff member's UUID to their authorized hardware installation token.
 * Prevents account impersonation and credential/nickname hijacking on offline or low-security servers.
 */
public class DeviceLockRecord {

    private UUID accountUUID;
    private String lastAccountName;
    private String boundToken;

    // Wall-clock calendar timestamps persisted across restarts for audit/display,
    // intentionally using System.currentTimeMillis() per AGENT.md Invariant 6.
    private long boundEpoch;
    private long lastSeenEpoch;

    /**
     * Default constructor for Gson deserialization.
     */
    public DeviceLockRecord() {
    }

    public DeviceLockRecord(@NotNull UUID accountUUID, @Nullable String lastAccountName, @NotNull String boundToken) {
        this.accountUUID = accountUUID;
        this.lastAccountName = (lastAccountName != null && !lastAccountName.isBlank()) ? lastAccountName : "Unknown";
        this.boundToken = boundToken;
        this.boundEpoch = System.currentTimeMillis();
        this.lastSeenEpoch = this.boundEpoch;
    }

    public DeviceLockRecord(@NotNull UUID accountUUID, @Nullable String lastAccountName, @NotNull String boundToken, long boundEpoch, long lastSeenEpoch) {
        this.accountUUID = accountUUID;
        this.lastAccountName = (lastAccountName != null && !lastAccountName.isBlank()) ? lastAccountName : "Unknown";
        this.boundToken = boundToken;
        this.boundEpoch = boundEpoch;
        this.lastSeenEpoch = lastSeenEpoch;
    }

    @NotNull
    public UUID getAccountUUID() {
        return accountUUID;
    }

    @NotNull
    public String getLastAccountName() {
        return lastAccountName != null ? lastAccountName : "Unknown";
    }

    public void setLastAccountName(@Nullable String lastAccountName) {
        this.lastAccountName = (lastAccountName != null && !lastAccountName.isBlank()) ? lastAccountName : "Unknown";
    }

    @NotNull
    public String getBoundToken() {
        return boundToken != null ? boundToken : "";
    }

    public void setBoundToken(@NotNull String boundToken) {
        this.boundToken = boundToken;
    }

    public long getBoundEpoch() {
        return boundEpoch;
    }

    public long getLastSeenEpoch() {
        return lastSeenEpoch;
    }

    public void updateLastSeen() {
        this.lastSeenEpoch = System.currentTimeMillis();
    }

    public void setLastSeenEpoch(long lastSeenEpoch) {
        this.lastSeenEpoch = lastSeenEpoch;
    }
}
