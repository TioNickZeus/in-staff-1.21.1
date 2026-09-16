package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.access.PlaytimeTracker;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Playtime tracking and activity audit commands (/playtime and /seen).
 * Supports viewing personal accumulated playtime as well as staff inspection of last seen activity.
 */
public final class PlaytimeCommand {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private PlaytimeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerPlaytime(dispatcher);
        registerSeen(dispatcher);
    }

    private static void registerPlaytime(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playtime")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (source.getEntity() instanceof ServerPlayer player) {
                        String formattedTime = PlaytimeTracker.getInstance().getFormattedPlaytime(player.getUUID());
                        source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.playtime.self", formattedTime), false);
                        return 1;
                    } else {
                        source.sendFailure(Component.literal("§cConsole must specify a target player: /playtime <player>"));
                        return 0;
                    }
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> source.hasPermission(2))
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> {
                            String targetName = StringArgumentType.getString(ctx, "player");
                            CommandSourceStack source = ctx.getSource();
                            UUID targetUUID = PlayerResolver.resolveUUID(source.getServer(), targetName);

                            boolean isOnline = PlaytimeTracker.getInstance().isOnline(targetUUID);
                            String statusStr = isOnline
                                    ? LocalizationHelper.getRawTranslation("instaff.common.online")
                                    : LocalizationHelper.getRawTranslation("instaff.common.offline");
                            String formattedTime = PlaytimeTracker.getInstance().getFormattedPlaytime(targetUUID);

                            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.playtime.other",
                                    targetName, formattedTime, statusStr), false);
                            return 1;
                        })));
    }

    private static void registerSeen(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("seen")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> {
                            String targetName = StringArgumentType.getString(ctx, "player");
                            CommandSourceStack source = ctx.getSource();
                            MinecraftServer server = source.getServer();
                            UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);

                            PlaytimeTracker.PlaytimeData data = PlaytimeTracker.getInstance().getPlaytimeData(targetUUID);
                            boolean isOnline = PlaytimeTracker.getInstance().isOnline(targetUUID);

                            String statusStr = isOnline
                                    ? LocalizationHelper.getRawTranslation("instaff.common.online")
                                    : LocalizationHelper.getRawTranslation("instaff.common.offline");

                            String firstJoined = (data != null && data.getFirstSeenEpoch() > 0)
                                    ? DATE_FORMATTER.format(Instant.ofEpochMilli(data.getFirstSeenEpoch()))
                                    : LocalizationHelper.getRawTranslation("instaff.common.unknown");

                            String lastSeen;
                            if (isOnline) {
                                lastSeen = LocalizationHelper.getRawTranslation("instaff.common.online");
                            } else if (data != null && data.getLastSeenEpoch() > 0) {
                                lastSeen = DATE_FORMATTER.format(Instant.ofEpochMilli(data.getLastSeenEpoch()));
                            } else {
                                lastSeen = LocalizationHelper.getRawTranslation("instaff.common.never");
                            }

                            String totalPlaytime = PlaytimeTracker.getInstance().getFormattedPlaytime(targetUUID);

                            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.seen.result",
                                    targetName, statusStr, lastSeen, firstJoined, totalPlaytime), false);
                            return 1;
                        })));
    }
}
