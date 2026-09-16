package com.tio.instaff.moderation;

import com.tio.instaff.InStaff;
import com.tio.instaff.access.MaintenanceManager;
import com.tio.instaff.access.PlaytimeTracker;
import com.tio.instaff.access.WhitelistManager;
import com.tio.instaff.util.DurationParser;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.TextUtil;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event handler for server-authoritative moderation enforcement:
 * - Intercepts player login for maintenance, whitelist, and bans.
 * - Intercepts chat messages for active mutes.
 * - Clamps movement and denies interactions for frozen players.
 * - Manages session timers for the playtime tracker.
 */
@EventBusSubscriber(modid = InStaff.MODID)
public final class ModerationEventHandler {

    private static final Map<UUID, Vec3> FREEZE_POSITIONS = new ConcurrentHashMap<>();

    private ModerationEventHandler() {
    }

    @SubscribeEvent
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

        // 3. Check Active Ban (UUID first)
        Optional<PunishmentRecord> activeBan = PunishmentManager.getInstance().getActiveBan(uuid);

        // Offline Ban Evasion check: if not banned by UUID, check IP address
        if (activeBan.isEmpty() && player.getIpAddress() != null) {
            activeBan = PunishmentManager.getInstance().getActiveBanByIp(player.getIpAddress());
        }

        if (activeBan.isPresent()) {
            PunishmentRecord ban = activeBan.get();
            MutableComponent kickMessage;
            if (ban.getExpiresAtEpoch() == -1L) {
                kickMessage = LocalizationHelper.getMessage("instaff.punishment.banned",
                        ban.getReason(), ban.getStaffName(), LocalizationHelper.getRawTranslation("instaff.common.permanent"));
            } else {
                kickMessage = LocalizationHelper.getMessage("instaff.punishment.tempbanned",
                        ban.getReason(), ban.getStaffName(), DurationParser.formatRemaining(ban.getExpiresAtEpoch()));
            }
            player.connection.disconnect(kickMessage);
            return;
        }

        // 4. Start Playtime Tracking
        PlaytimeTracker.getInstance().onPlayerJoin(uuid, player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();
        PlaytimeTracker.getInstance().onPlayerLeave(uuid);
        FREEZE_POSITIONS.remove(uuid);
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        Optional<PunishmentRecord> activeMute = PunishmentManager.getInstance().getActiveMute(player.getUUID());
        if (activeMute.isPresent()) {
            PunishmentRecord mute = activeMute.get();
            event.setCanceled(true);

            String remaining = DurationParser.formatRemaining(mute.getExpiresAtEpoch());
            player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.muted", remaining, mute.getReason()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();
        if (PunishmentManager.getInstance().isFrozen(uuid)) {
            Vec3 frozenPos = FREEZE_POSITIONS.computeIfAbsent(uuid, k -> player.position());

            // Clamp position if moved away
            if (player.distanceToSqr(frozenPos) > 0.04) {
                player.teleportTo(player.serverLevel(), frozenPos.x, frozenPos.y, frozenPos.z, player.getYRot(), player.getXRot());
            }
            player.setDeltaMovement(0, 0, 0);

            // Periodic warning every 100 ticks (5 seconds)
            if (player.tickCount % 100 == 0) {
                player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.frozen"));
            }
        } else {
            FREEZE_POSITIONS.remove(uuid);
        }
    }

    // Freeze interaction protections
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (PunishmentManager.getInstance().isFrozen(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (PunishmentManager.getInstance().isFrozen(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (PunishmentManager.getInstance().isFrozen(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (PunishmentManager.getInstance().isFrozen(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (PunishmentManager.getInstance().isFrozen(event.getPlayer().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PunishmentManager.getInstance().isFrozen(player.getUUID())) {
            event.setCanceled(true);
        }
    }
}
