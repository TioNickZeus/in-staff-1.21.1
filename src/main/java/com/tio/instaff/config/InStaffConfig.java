package com.tio.instaff.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Global configuration specification for In-Staff.
 */
public final class InStaffConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Specific config sections will be added here per module

    public static final ModConfigSpec SPEC = BUILDER.build();

    private InStaffConfig() {
    }
}