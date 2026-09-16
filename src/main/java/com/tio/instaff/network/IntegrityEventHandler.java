package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Event handler for network integrity.
 */
@EventBusSubscriber(modid = InStaff.MODID)
public final class IntegrityEventHandler {

    private IntegrityEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerIntegrityValidator.getInstance().initiateHandshake(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerIntegrityValidator.getInstance().onPlayerLeave(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerIntegrityValidator.getInstance().tickWatchdog(player);
            }
        } catch (Throwable t) {
            System.err.println("[In-Staff] Error in IntegrityEventHandler.onPlayerTick: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
