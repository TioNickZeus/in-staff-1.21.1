package com.tio.instaff.moderation;

import com.tio.instaff.InStaff;
import com.tio.instaff.access.MaintenanceManager;
import com.tio.instaff.access.PlaytimeTracker;
import com.tio.instaff.access.WhitelistManager;
import com.tio.instaff.config.InStaffConfig;
import com.tio.instaff.util.DurationParser;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.TextUtil;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event handler for server-authoritative moderation enforcement:
 * - Intercepts chat messages and commands for active mutes.
 * - Clamps movement and denies interactions for frozen players.
 */
@EventBusSubscriber(modid = InStaff.MODID)
public final class ModerationEventHandler {

    private static final Map<UUID, Vec3> FREEZE_POSITIONS = new ConcurrentHashMap<>();

    private ModerationEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FREEZE_POSITIONS.remove(player.getUUID());
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

            String remaining = DurationParser.formatDuration(mute.getRemainingMillis());
            player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.muted", remaining, mute.getReason()));
        }
    }

    @SubscribeEvent
    public static void onCommandEvent(CommandEvent event) {
        if (event.getParseResults() == null || event.getParseResults().getContext() == null) {
            return;
        }

        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Optional<PunishmentRecord> activeMute = PunishmentManager.getInstance().getActiveMute(player.getUUID());
        if (activeMute.isEmpty()) {
            return;
        }

        var nodes = event.getParseResults().getContext().getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        String commandName = nodes.get(0).getNode().getName();
        if (commandName == null || commandName.isBlank()) {
            return;
        }

        String normalized = commandName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }

        List<? extends String> blockedCommands = InStaffConfig.getBlockedMuteCommands();
        boolean isBlocked = false;
        for (String blocked : blockedCommands) {
            if (blocked == null) {
                continue;
            }
            String normalizedBlocked = blocked.toLowerCase(Locale.ROOT).trim();
            if (normalizedBlocked.startsWith("/")) {
                normalizedBlocked = normalizedBlocked.substring(1);
            }
            if (normalizedBlocked.startsWith("minecraft:")) {
                normalizedBlocked = normalizedBlocked.substring("minecraft:".length());
            }
            if (normalized.equals(normalizedBlocked)) {
                isBlocked = true;
                break;
            }
        }

        if (isBlocked) {
            event.setCanceled(true);
            PunishmentRecord mute = activeMute.get();
            String remaining = DurationParser.formatDuration(mute.getRemainingMillis());
            player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.muted", remaining, mute.getReason()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        try {
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
        } catch (Throwable t) {
            InStaff.LOGGER.error("[In-Staff] Error in ModerationEventHandler.onPlayerTick", t);
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
