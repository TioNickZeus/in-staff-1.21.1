package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.inspection.EnderseeMenu;
import com.tio.instaff.inspection.InvseeMenu;
import com.tio.instaff.inspection.OfflinePlayerDataHelper;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inspection commands (/invsee and /endersee).
 * Enables bidirectional live inspection of online players and atomic NBT manipulation
 * for offline player inventories.
 * Requires OP permission level 2.
 */
public final class InvseeCommand {

    /**
     * UUIDs currently being inspected through an offline (NBT snapshot) session.
     * Two concurrent snapshots of the same player would silently discard one staff member's
     * edits when the second menu closes, so only one offline session per player is allowed.
     */
    private static final Set<UUID> ACTIVE_OFFLINE_SESSIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private InvseeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerInvsee(dispatcher);
        registerEndersee(dispatcher);
    }

    private static void registerInvsee(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("invsee")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> executeInvsee(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
    }

    private static int executeInvsee(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer staffPlayer)) {
            source.sendFailure(Component.literal("§cThis command can only be executed by a player in-game."));
            return 0;
        }

        if (staffPlayer.getGameProfile().getName().equalsIgnoreCase(targetName)) {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.invsee.cannot_self"));
            return 0;
        }

        MinecraftServer server = source.getServer();
        ServerPlayer onlineTarget = server.getPlayerList().getPlayerByName(targetName);

        if (onlineTarget != null) {
            String displayName = onlineTarget.getGameProfile().getName();
            UUID targetUUID = onlineTarget.getUUID();

            staffPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new InvseeMenu(id, inv, onlineTarget.getInventory(), displayName, targetUUID, false, null),
                    Component.translatableWithFallback("instaff.screen.invsee.title", "Inventory: %s", displayName)
            ), buffer -> {
                buffer.writeUtf(displayName);
                buffer.writeUUID(targetUUID);
                buffer.writeBoolean(false);
            });

            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.invsee.opened",
                    displayName, LocalizationHelper.getRawTranslation("instaff.common.online")), false);
            return 1;
        } else {
            // Offline player inspection
            UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);

            if (!ACTIVE_OFFLINE_SESSIONS.add(targetUUID)) {
                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.invsee.already_open", targetName));
                return 0;
            }

            OfflinePlayerDataHelper.OfflineInspectionResult result = OfflinePlayerDataHelper.loadOfflineInventory(server, targetUUID);

            if (result == null) {
                ACTIVE_OFFLINE_SESSIONS.remove(targetUUID);
                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.invsee.not_found", targetName));
                return 0;
            }

            Runnable saveCallback = guardedOfflineSave(server, staffPlayer, targetUUID, targetName, result);

            staffPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new InvseeMenu(id, inv, result.getContainer(), targetName, targetUUID, true, saveCallback),
                    Component.translatableWithFallback("instaff.screen.invsee.title", "Inventory: %s", targetName)
            ), buffer -> {
                buffer.writeUtf(targetName);
                buffer.writeUUID(targetUUID);
                buffer.writeBoolean(true);
            });

            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.invsee.opened",
                    targetName, LocalizationHelper.getRawTranslation("instaff.common.offline")), false);
            return 1;
        }
    }

    private static void registerEndersee(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("endersee")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> executeEndersee(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
    }

    private static int executeEndersee(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer staffPlayer)) {
            source.sendFailure(Component.literal("§cThis command can only be executed by a player in-game."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        ServerPlayer onlineTarget = server.getPlayerList().getPlayerByName(targetName);

        if (onlineTarget != null) {
            String displayName = onlineTarget.getGameProfile().getName();
            UUID targetUUID = onlineTarget.getUUID();

            staffPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new EnderseeMenu(id, inv, onlineTarget.getEnderChestInventory(), displayName, targetUUID, false, null),
                    Component.translatableWithFallback("instaff.screen.endersee.title", "Ender Chest: %s", displayName)
            ), buffer -> {
                buffer.writeUtf(displayName);
                buffer.writeUUID(targetUUID);
                buffer.writeBoolean(false);
            });

            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.endersee.opened",
                    displayName, LocalizationHelper.getRawTranslation("instaff.common.online")), false);
            return 1;
        } else {
            // Offline player enderchest inspection
            UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);

            if (!ACTIVE_OFFLINE_SESSIONS.add(targetUUID)) {
                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.invsee.already_open", targetName));
                return 0;
            }

            OfflinePlayerDataHelper.OfflineInspectionResult result = OfflinePlayerDataHelper.loadOfflineEnderChest(server, targetUUID);

            if (result == null) {
                ACTIVE_OFFLINE_SESSIONS.remove(targetUUID);
                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.invsee.not_found", targetName));
                return 0;
            }

            Runnable saveCallback = guardedOfflineSave(server, staffPlayer, targetUUID, targetName, result);

            staffPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new EnderseeMenu(id, inv, result.getContainer(), targetName, targetUUID, true, saveCallback),
                    Component.translatableWithFallback("instaff.screen.endersee.title", "Ender Chest: %s", targetName)
            ), buffer -> {
                buffer.writeUtf(targetName);
                buffer.writeUUID(targetUUID);
                buffer.writeBoolean(true);
            });

            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.endersee.opened",
                    targetName, LocalizationHelper.getRawTranslation("instaff.common.offline")), false);
            return 1;
        }
    }

    /**
     * Wraps an offline save callback so it never writes a stale snapshot over live data.
     * The snapshot is taken when the menu opens; if the target reconnects before the menu is
     * closed, writing it back would overwrite the data the server now holds in memory (and would
     * itself be overwritten on their next logout), so the write is skipped and staff are told.
     */
    private static Runnable guardedOfflineSave(MinecraftServer server,
                                               ServerPlayer staffPlayer,
                                               UUID targetUUID,
                                               String targetName,
                                               OfflinePlayerDataHelper.OfflineInspectionResult result) {
        return () -> {
            try {
                if (server.getPlayerList().getPlayer(targetUUID) != null) {
                    staffPlayer.sendSystemMessage(
                            LocalizationHelper.getPrefixedMessage("instaff.command.invsee.save_aborted", targetName));
                    return;
                }
                result.getSaveCallback().run();
            } finally {
                ACTIVE_OFFLINE_SESSIONS.remove(targetUUID);
            }
        };
    }
}
