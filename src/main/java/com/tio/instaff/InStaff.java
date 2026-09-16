package com.tio.instaff;

import com.mojang.logging.LogUtils;
import com.tio.instaff.config.InStaffConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Common entrypoint for In-Staff.
 * Complete administration, moderation, and security suite for Minecraft 1.21.1 (NeoForge).
 * Loaded on both client and server sides.
 */
@Mod(InStaff.MODID)
public final class InStaff {

    public static final String MODID = "instaff";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InStaff(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("In-Staff starting up (Minecraft 1.21.1 / NeoForge)");

        // SERVER config — only the server controls moderation rules
        modContainer.registerConfig(ModConfig.Type.SERVER, InStaffConfig.SPEC);
    }
}