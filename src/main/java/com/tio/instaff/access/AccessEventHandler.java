package com.tio.instaff.access;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.tio.instaff.InStaff;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Collection;
import java.util.Locale;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCommandEvent(CommandEvent event) {
        try {
            if (!DeviceLockManager.getInstance().isEnabled()) {
                return;
            }
            if (event.getException() != null) {
                return;
            }

            ParseResults<CommandSourceStack> parseResults = event.getParseResults();
            if (parseResults == null
                    || parseResults.getContext() == null
                    || !parseResults.getExceptions().isEmpty()
                    || parseResults.getReader().canRead()) {
                return;
            }

            CommandContextBuilder<CommandSourceStack> ctx = parseResults.getContext();
            CommandSourceStack source = ctx.getSource();
            if (source == null || !source.hasPermission(3)) {
                return;
            }

            var nodes = ctx.getNodes();
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
            if (!"op".equals(normalized)) {
                return;
            }

            ParsedArgument<CommandSourceStack, ?> targetsArg = ctx.getArguments().get("targets");
            if (targetsArg == null) {
                return;
            }

            Object result = targetsArg.getResult();
            MinecraftServer server = source.getServer();
            if (result instanceof GameProfileArgument.Result profileResult) {
                Collection<GameProfile> profiles = profileResult.getNames(source);
                for (GameProfile profile : profiles) {
                    if (profile == null) {
                        continue;
                    }
                    String targetName = profile.getName();
                    UUID targetUUID = profile.getId();
                    if (targetUUID == null && targetName != null && !targetName.isBlank()) {
                        targetUUID = PlayerResolver.resolveUUID(server, targetName);
                    }
                    if (targetUUID != null) {
                        DeviceLockManager.getInstance().tryAutoBindOnOp(targetUUID, targetName);
                    }
                }
            } else if (result instanceof String targetName && !targetName.isBlank()) {
                UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);
                DeviceLockManager.getInstance().tryAutoBindOnOp(targetUUID, targetName);
            }
        } catch (Throwable t) {
            InStaff.LOGGER.error("[In-Staff] Error processing /op auto-bind in AccessEventHandler.onCommandEvent", t);
        }
    }
}
