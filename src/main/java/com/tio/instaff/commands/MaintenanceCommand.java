package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.access.MaintenanceManager;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Administrative maintenance command (/maintenance).
 * Subcommands: on, off, status, bypass add <player>, bypass remove <player>.
 * Requires OP permission level 2.
 */
public final class MaintenanceCommand {

    private MaintenanceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("maintenance")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on")
                        .executes(ctx -> {
                            MaintenanceManager.getInstance().setMaintenance(true);
                            MinecraftServer server = ctx.getSource().getServer();

                            // Disconnect unauthorized players currently online
                            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                if (!MaintenanceManager.getInstance().isAllowed(player.getUUID(), player.hasPermissions(2))) {
                                    player.connection.disconnect(LocalizationHelper.getMessage("instaff.maintenance.kick_message"));
                                }
                            }

                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.maintenance.enabled"), true);
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            MaintenanceManager.getInstance().setMaintenance(false);
                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.maintenance.disabled"), true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            boolean active = MaintenanceManager.getInstance().isMaintenance();
                            int bypassCount = MaintenanceManager.getInstance().getStaffBypassList().size();
                            String statusStr = active ? LocalizationHelper.getRawTranslation("instaff.common.active") : LocalizationHelper.getRawTranslation("instaff.common.offline");

                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.maintenance.status", statusStr, bypassCount), false);
                            return 1;
                        }))
                .then(Commands.literal("bypass")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                                        .executes(ctx -> {
                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            UUID targetUUID = PlayerResolver.resolveUUID(ctx.getSource().getServer(), targetName);
                                            MaintenanceManager.getInstance().addStaffBypass(targetUUID);

                                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.maintenance.bypass_add", targetName), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                                        .executes(ctx -> {
                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            UUID targetUUID = PlayerResolver.resolveUUID(ctx.getSource().getServer(), targetName);
                                            MaintenanceManager.getInstance().removeStaffBypass(targetUUID);

                                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.maintenance.bypass_remove", targetName), true);
                                            return 1;
                                        })))));
    }
}
