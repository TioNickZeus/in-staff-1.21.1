package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.access.WhitelistManager;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Map;
import java.util.UUID;

/**
 * Smart Whitelist management command (/swhitelist).
 * Subcommands: on, off, add <player>, remove <player>, list, reload.
 * Requires OP permission level 2.
 */
public final class WhitelistCommand {

    private WhitelistCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("swhitelist")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on")
                        .executes(ctx -> {
                            WhitelistManager.getInstance().setEnabled(true);
                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.whitelist.enabled"), true);
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            WhitelistManager.getInstance().setEnabled(false);
                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.whitelist.disabled"), true);
                            return 1;
                        }))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                                .executes(ctx -> {
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    UUID targetUUID = PlayerResolver.resolveUUID(ctx.getSource().getServer(), targetName);
                                    WhitelistManager.getInstance().addPlayer(targetUUID, targetName);

                                    ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.whitelist.add", targetName), true);
                                    return 1;
                                })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(WhitelistManager.getInstance().getAllowedPlayers().values(), builder))
                                .executes(ctx -> {
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    UUID targetUUID = PlayerResolver.resolveUUID(ctx.getSource().getServer(), targetName);
                                    WhitelistManager.getInstance().removePlayer(targetUUID);

                                    ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.whitelist.remove", targetName), true);
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            Map<UUID, String> allowed = WhitelistManager.getInstance().getAllowedPlayers();
                            String listStr = allowed.isEmpty()
                                    ? LocalizationHelper.getRawTranslation("instaff.common.none")
                                    : String.join(", ", allowed.values());

                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.whitelist.list", allowed.size(), listStr), false);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            WhitelistManager.getInstance().reload();
                            int count = WhitelistManager.getInstance().getAllowedPlayers().size();
                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.whitelist.reload", count), true);
                            return 1;
                        })));
    }
}
