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

        UUID uuid = player.getUUID();
        boolean isStaff = player.hasPermissions(2);

        // 1. Check Maintenance Mode
        if (!MaintenanceManager.getInstance().isAllowed(uuid, isStaff)) {
            player.connection.disconnect(LocalizationHelper.getMessage("instaff.maintenance.kick_message"));
            return;
        }

        // 2. Check Smart Whitelist
        if (WhitelistManager.getInstance().isEnabled() && !isStaff) {
            if (!WhitelistManager.getInstance().isWhitelisted(uuid)) {
                player.connection.disconnect(LocalizationHelper.getMessage("instaff.whitelist.kick_message"));
                return;
            }
        }

        // 3. Start Playtime Tracking
        PlaytimeTracker.getInstance().onPlayerJoin(uuid, player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlaytimeTracker.getInstance().onPlayerLeave(player.getUUID());
    }
}
