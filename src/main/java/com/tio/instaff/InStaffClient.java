package com.tio.instaff;

import com.mojang.logging.LogUtils;
import com.tio.instaff.client.config.InStaffClientConfig;
import com.tio.instaff.client.integrity.ClientHashScanner;
import com.tio.instaff.client.screen.EnderseeScreen;
import com.tio.instaff.client.screen.InvseeScreen;
import com.tio.instaff.client.screen.MultiplayerConsentHandler;
import com.tio.instaff.inspection.ModMenus;
import com.tio.instaff.network.InStaffNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
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

        // Register client config (privacy/consent preferences)
        modContainer.registerConfig(ModConfig.Type.CLIENT, InStaffClientConfig.SPEC);

        // Register client screens and multiplayer consent prompt
        modEventBus.addListener(RegisterMenuScreensEvent.class, this::registerScreens);
        NeoForge.EVENT_BUS.addListener(MultiplayerConsentHandler::onScreenOpening);

        // Register client integrity scanner handler
        InStaffNetwork.clientRequestHandler = ClientHashScanner::handleIntegrityRequest;
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.INVSEE.get(), InvseeScreen::new);
        event.register(ModMenus.ENDERSEE.get(), EnderseeScreen::new);
        LOGGER.info("In-Staff inspection screens registered (InvseeScreen, EnderseeScreen)");
    }
}