package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.access.DeviceLockManager;
import com.tio.instaff.access.DeviceLockRecord;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command suite for Staff Device Lock (/isdevice):
 * - /isdevice bind <player> <token> (Console only)
 * - /isdevice unbind <player> (Console only)
 * - /isdevice info <player> (OP level >= 2)
 * - /isdevice list (OP level >= 2)
 */
public final class DeviceLockCommand {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private DeviceLockCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("isdevice")
                .then(Commands.literal("bind")
                        .requires(source -> source.getEntity() == null) // Console-only enforcement
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                                .then(Commands.argument("token", StringArgumentType.string())
                                        .executes(ctx -> executeBind(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "token"))))))
                .then(Commands.literal("unbind")
                        .requires(source -> source.getEntity() == null) // Console-only enforcement
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                                .executes(ctx -> executeUnbind(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                                .executes(ctx -> executeInfo(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeList(ctx.getSource())))
        );
    }

    private static int executeBind(CommandSourceStack source, String targetName, String token) {
        UUID targetUUID = PlayerResolver.resolveUUID(source.getServer(), targetName);
        DeviceLockManager.getInstance().bindDirect(targetUUID, targetName, token);

        String truncated = truncateToken(token);
        source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.devicelock.bound", targetName, truncated), true);
        return 1;
    }

    private static int executeUnbind(CommandSourceStack source, String targetName) {
        UUID targetUUID = PlayerResolver.resolveUUID(source.getServer(), targetName);
        boolean removed = DeviceLockManager.getInstance().unbindDirect(targetUUID);

        if (removed) {
            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.devicelock.unbound", targetName), true);
            return 1;
        } else {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.devicelock.not_bound", targetName));
            return 0;
        }
    }

    private static int executeInfo(CommandSourceStack source, String targetName) {
        UUID targetUUID = PlayerResolver.resolveUUID(source.getServer(), targetName);
        Optional<DeviceLockRecord> recordOpt = DeviceLockManager.getInstance().getRecord(targetUUID);

        if (recordOpt.isEmpty()) {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.devicelock.not_bound", targetName));
            return 0;
        }

        DeviceLockRecord record = recordOpt.get();
        String truncatedToken = truncateToken(record.getBoundToken());
        String boundDate = DATE_FORMATTER.format(Instant.ofEpochMilli(record.getBoundEpoch()));
        String lastSeenDate = DATE_FORMATTER.format(Instant.ofEpochMilli(record.getLastSeenEpoch()));

        source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.devicelock.info",
                record.getLastAccountName(), truncatedToken, boundDate, lastSeenDate), false);
        return 1;
    }

    private static int executeList(CommandSourceStack source) {
        List<DeviceLockRecord> all = DeviceLockManager.getInstance().listAll();
        if (all.isEmpty()) {
            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.devicelock.list.empty"), false);
            return 1;
        }

        source.sendSuccess(() -> LocalizationHelper.getMessage("instaff.command.devicelock.list.header", all.size()), false);
        for (DeviceLockRecord record : all) {
            String truncated = truncateToken(record.getBoundToken());
            String lastSeen = DATE_FORMATTER.format(Instant.ofEpochMilli(record.getLastSeenEpoch()));
            source.sendSuccess(() -> LocalizationHelper.getMessage("instaff.command.devicelock.list.entry",
                    record.getLastAccountName(), truncated, lastSeen), false);
        }
        return all.size();
    }

    private static String truncateToken(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return "unknown";
        }
        return token.length() > 12 ? token.substring(0, 12) + "..." : token;
    }
}
