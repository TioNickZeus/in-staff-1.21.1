package com.tio.instaff.moderation;

/**
 * Enumeration of all punishment types supported by In-Staff.
 */
public enum PunishmentType {
    BAN,
    TEMP_BAN,
    MUTE,
    TEMP_MUTE,
    FREEZE,
    KICK;

    public boolean isTemporary() {
        return this == TEMP_BAN || this == TEMP_MUTE;
    }

    public boolean isBan() {
        return this == BAN || this == TEMP_BAN;
    }

    public boolean isMute() {
        return this == MUTE || this == TEMP_MUTE;
    }

    public boolean isFreeze() {
        return this == FREEZE;
    }
}
