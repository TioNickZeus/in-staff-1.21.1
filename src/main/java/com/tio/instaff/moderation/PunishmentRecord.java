package com.tio.instaff.moderation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable/persistent record representing a moderation action (ban, mute, kick, freeze).
 * Stores audit metadata, expiration, and offline ban evasion markers (IP + client token).
 */
public class PunishmentRecord {

    public static final UUID SERVER_UUID = new UUID(0L, 0L);
    public static final String CONSOLE_NAME = "Server";

    private UUID id;
    private UUID targetUUID;
    private String targetName;
    private UUID staffUUID;
    private String staffName;
    private PunishmentType type;
    private String reason;
    private long createdAtEpoch;
    private long expiresAtEpoch;
    private boolean active;
    private String ipAddress;
    private String clientToken;

    private long revokedAtEpoch;
    private UUID revokedByStaffUUID;
    private String revokedByStaffName;
    private String revokeReason;

    private transient long monotonicExpiry = 0;

    /**
     * Default constructor for Gson deserialization.
     */
    public PunishmentRecord() {
    }

    public PunishmentRecord(
            @NotNull UUID targetUUID,
            @NotNull String targetName,
            @Nullable UUID staffUUID,
            @Nullable String staffName,
            @NotNull PunishmentType type,
            @Nullable String reason,
            long durationMillis,
            @Nullable String ipAddress,
            @Nullable String clientToken
    ) {
        this.id = UUID.randomUUID();
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.staffUUID = (staffUUID != null) ? staffUUID : SERVER_UUID;
        this.staffName = (staffName != null && !staffName.isBlank()) ? staffName : CONSOLE_NAME;
        this.type = type;
        this.reason = (reason != null && !reason.isBlank()) ? reason : "No reason specified";
        this.createdAtEpoch = System.currentTimeMillis();
        this.expiresAtEpoch = (durationMillis <= 0 || durationMillis == -1) ? -1L : (this.createdAtEpoch + durationMillis);
        
        if (durationMillis <= 0 || durationMillis == -1) {
            this.monotonicExpiry = -1L;
        } else {
            this.monotonicExpiry = net.minecraft.Util.getMillis() + durationMillis;
        }
        
        // Kicks are instantaneous events, not active ongoing states
        this.active = (type != PunishmentType.KICK);
        this.ipAddress = ipAddress;
        this.clientToken = clientToken;
    }

    private long getMonotonicExpiry() {
        if (this.monotonicExpiry == 0) {
            long remaining = Math.max(0, this.expiresAtEpoch - System.currentTimeMillis());
            this.monotonicExpiry = net.minecraft.Util.getMillis() + remaining;
        }
        return this.monotonicExpiry;
    }

    public boolean isExpired() {
        if (expiresAtEpoch == -1L) {
            return false;
        }
        return getMonotonicExpiry() <= net.minecraft.Util.getMillis();
    }

    public boolean isActive() {
        return active && !isExpired();
    }

    public boolean isRevoked() {
        return revokedAtEpoch > 0;
    }

    public void revoke(@Nullable UUID staffUUID, @Nullable String staffName, @Nullable String reason) {
        this.active = false;
        this.revokedAtEpoch = System.currentTimeMillis();
        this.revokedByStaffUUID = (staffUUID != null) ? staffUUID : SERVER_UUID;
        this.revokedByStaffName = (staffName != null && !staffName.isBlank()) ? staffName : CONSOLE_NAME;
        this.revokeReason = (reason != null && !reason.isBlank()) ? reason : "Revoked by staff";
    }

    public long getRemainingMillis() {
        if (expiresAtEpoch == -1L) {
            return -1L;
        }
        long diff = getMonotonicExpiry() - net.minecraft.Util.getMillis();
        return Math.max(0L, diff);
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public String getTargetName() {
        return targetName != null ? targetName : "Unknown";
    }

    public UUID getStaffUUID() {
        return staffUUID != null ? staffUUID : SERVER_UUID;
    }

    public String getStaffName() {
        return staffName != null ? staffName : CONSOLE_NAME;
    }

    public PunishmentType getType() {
        return type;
    }

    public String getReason() {
        return reason != null ? reason : "";
    }

    public long getCreatedAtEpoch() {
        return createdAtEpoch;
    }

    public long getExpiresAtEpoch() {
        return expiresAtEpoch;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public long getRevokedAtEpoch() {
        return revokedAtEpoch;
    }

    public UUID getRevokedByStaffUUID() {
        return revokedByStaffUUID;
    }

    public String getRevokedByStaffName() {
        return revokedByStaffName;
    }

    public String getRevokeReason() {
        return revokeReason;
    }
}
