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

import java.util.UUID;

/**
 * Inspection commands (/invsee and /endersee).
 * Enables bidirectional live inspection of online players and atomic NBT manipulation
 * for offline player inventories.
 * Requires OP permission level 2.
 */
public final class InvseeCommand {

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
            OfflinePlayerDataHelper.OfflineInspectionResult result = OfflinePlayerDataHelper.loadOfflineInventory(server, targetUUID);

            if (result == null) {
                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.invsee.not_found", targetName));
                return 0;
            }

            staffPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new InvseeMenu(id, inv, result.getContainer(), targetName, targetUUID, true, result.getSaveCallback()),
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
            OfflinePlayerDataHelper.OfflineInspectionResult result = OfflinePlayerDataHelper.loadOfflineEnderChest(server, targetUUID);

            if (result == null) {
                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.invsee.not_found", targetName));
                return 0;
            }

            staffPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new EnderseeMenu(id, inv, result.getContainer(), targetName, targetUUID, true, result.getSaveCallback()),
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
}
