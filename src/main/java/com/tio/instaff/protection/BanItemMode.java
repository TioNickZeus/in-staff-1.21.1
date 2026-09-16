package com.tio.instaff.protection;

/**
 * Restriction modes for banned items in In-Staff.
 */
public enum BanItemMode {
    /**
     * Total restriction: item cannot be picked up, used, placed, or held in inventory.
     */
    TOTAL,

    /**
     * Use denial: player cannot right-click or activate the item.
     */
    NO_USE,

    /**
     * Placement denial: item cannot be placed as a block in the world.
     */
    NO_PLACE;

    public boolean blocksUse() {
        return this == TOTAL || this == NO_USE;
    }

    public boolean blocksPlacement() {
        return this == TOTAL || this == NO_PLACE;
    }

    public boolean blocksPossession() {
        return this == TOTAL;
    }
}
