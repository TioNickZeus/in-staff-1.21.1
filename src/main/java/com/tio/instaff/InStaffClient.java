package com.tio.instaff;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Client-only entrypoint for In-Staff.
 * Registers client screens (Invsee, Endersee), key bindings,
 * and the client integrity hash scanner.
 *
 * This class is only loaded on the CLIENT distribution.
 * Server code must never import from this class or from com.tio.instaff.client.*.
 */
@Mod(value = InStaff.MODID, dist = Dist.CLIENT)
public final class InStaffClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    public InStaffClient(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("In-Staff client module initializing");

        // TODO: Register client screens (InvseeScreen, EnderseeScreen)
        // TODO: Register client integrity scanner handler
    }
}