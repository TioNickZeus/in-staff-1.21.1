package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.moderation.PunishmentManager;
import com.tio.instaff.moderation.PunishmentRecord;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Punishment history audit command (/history and /checkpunish).
 * Displays full disciplinary audit trail for any player (online or offline).
 * Requires OP permission level 2.
 */
public final class HistoryCommand {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private HistoryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = Commands.literal("history")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> executeHistory(ctx.getSource(), StringArgumentType.getString(ctx, "player"))));

        dispatcher.register(command);

        // Alias /checkpunish
        dispatcher.register(Commands.literal("checkpunish")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> executeHistory(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
    }

    private static int executeHistory(CommandSourceStack source, String targetName) {
        UUID targetUUID = PlayerResolver.resolveUUID(source.getServer(), targetName);
        List<PunishmentRecord> records = PunishmentManager.getInstance().getHistory(targetUUID);

        if (records.isEmpty()) {
            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.history.empty", targetName), false);
            return 1;
        }

        source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.history.header", targetName, records.size()), false);

        for (PunishmentRecord record : records) {
            String dateStr = DATE_FORMATTER.format(Instant.ofEpochMilli(record.getCreatedAtEpoch()));
            String statusStr;
            if (record.isRevoked()) {
                statusStr = LocalizationHelper.getRawTranslation("instaff.common.revoked");
            } else if (record.isExpired()) {
                statusStr = LocalizationHelper.getRawTranslation("instaff.common.expired");
            } else {
                statusStr = LocalizationHelper.getRawTranslation("instaff.common.active");
            }

            source.sendSuccess(() -> LocalizationHelper.getMessage("instaff.command.history.entry",
                    dateStr, record.getType().name(), record.getStaffName(), record.getReason(), statusStr), false);
        }

        return 1;
    }
}
