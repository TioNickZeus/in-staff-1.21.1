package com.tio.instaff.access;

import com.tio.instaff.InStaff;
import com.tio.instaff.util.LocalizationHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

/**
 * Event handler for server-authoritative access control:
 * - Maintenance Mode
 * - Smart Whitelist
 * - Playtime Tracking
 */
@EventBusSubscriber(modid = InStaff.MODID)
public final class AccessEventHandler {

    private AccessEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Maintenance and Whitelist access control is enforced early during the configuration phase
        // via AccessCheckConfigurationTask. Here we only initialize Playtime tracking upon entering the world.
        PlaytimeTracker.getInstance().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlaytimeTracker.getInstance().onPlayerLeave(player.getUUID());
    }
}
