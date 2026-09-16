package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.InStaff;
import com.tio.instaff.config.InStaffConfig;
import com.tio.instaff.moderation.PunishmentManager;
import com.tio.instaff.moderation.PunishmentRecord;
import com.tio.instaff.moderation.PunishmentType;
import com.tio.instaff.util.DurationParser;
import com.tio.instaff.util.LocalizationHelper;
import com.tio.instaff.util.PlayerResolver;
import com.tio.instaff.network.ServerIntegrityValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Moderation and enforcement commands:
 * /ban, /tempban, /unban, /mute, /tempmute, /unmute, /kick, /freeze.
 * Requires permission level 2 (staff). Supports offline players and tab-completion.
 */
public final class PunishCommands {

    private static final String DEFAULT_BAN_REASON = LocalizationHelper.getRawTranslation("instaff.punishment.default_ban_reason");
    private static final String DEFAULT_MUTE_REASON = LocalizationHelper.getRawTranslation("instaff.punishment.default_mute_reason");
    private static final String DEFAULT_KICK_REASON = LocalizationHelper.getRawTranslation("instaff.punishment.default_kick_reason");

    private PunishCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerBan(dispatcher);
        registerTempBan(dispatcher);
        registerUnban(dispatcher);
        registerMute(dispatcher);
        registerTempMute(dispatcher);
        registerUnmute(dispatcher);
        registerKick(dispatcher);
        registerFreeze(dispatcher);
    }

    // -------------------------------------------------------------
    // /ban <player> [reason]
    // -------------------------------------------------------------
    private static void registerBan(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ban")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> executeBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), DEFAULT_BAN_REASON))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason"))))));
    }

    private static int executeBan(CommandSourceStack source, String targetName, String reason) {
        MinecraftServer server = source.getServer();
        UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);

        String ipAddress = (targetPlayer != null) ? targetPlayer.getIpAddress() : null;
        String clientToken = resolveClientToken(targetUUID);
        UUID staffUUID = getStaffUUID(source);
        String staffName = getStaffName(source);

        PunishmentRecord record = new PunishmentRecord(
                targetUUID, targetName, staffUUID, staffName,
                PunishmentType.BAN, reason, -1L, ipAddress, clientToken
        );
        PunishmentManager.getInstance().addPunishment(record);

        if (targetPlayer != null) {
            MutableComponent kickMessage = LocalizationHelper.getMessage("instaff.punishment.banned",
                    reason, staffName, LocalizationHelper.getRawTranslation("instaff.common.permanent"));
            targetPlayer.connection.disconnect(kickMessage);
        }

        MutableComponent successMsg = LocalizationHelper.getPrefixedMessage("instaff.command.ban.success", targetName, reason);
        announce(source, server, successMsg);
        return 1;
    }

    // -------------------------------------------------------------
    // /tempban <player> <duration> [reason]
    // -------------------------------------------------------------
    private static void registerTempBan(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tempban")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"30m", "1h", "12h", "1d", "7d", "30d"}, builder))
                                .executes(ctx -> executeTempBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "duration"), DEFAULT_BAN_REASON))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeTempBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "duration"), StringArgumentType.getString(ctx, "reason")))))));
    }

    private static int executeTempBan(CommandSourceStack source, String targetName, String durationStr, String reason) {
        OptionalLong durationOpt = DurationParser.tryParseDurationMillis(durationStr);
        if (durationOpt.isEmpty() || durationOpt.getAsLong() <= 0) {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.error.invalid_duration", durationStr));
            return 0;
        }
        long durationMillis = durationOpt.getAsLong();

        // Enforce the configured maximum temporary ban length.
        long maxDurationMillis = InStaffConfig.getMaxTempBanDays() * 86_400_000L;
        if (durationMillis > maxDurationMillis) {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.error.duration_too_long",
                    DurationParser.formatDuration(maxDurationMillis)));
            return 0;
        }

        MinecraftServer server = source.getServer();
        UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);

        String ipAddress = (targetPlayer != null) ? targetPlayer.getIpAddress() : null;
        String clientToken = resolveClientToken(targetUUID);
        UUID staffUUID = getStaffUUID(source);
        String staffName = getStaffName(source);

        PunishmentRecord record = new PunishmentRecord(
                targetUUID, targetName, staffUUID, staffName,
                PunishmentType.TEMP_BAN, reason, durationMillis, ipAddress, clientToken
        );
        PunishmentManager.getInstance().addPunishment(record);

        String formattedDuration = DurationParser.formatDuration(durationMillis);
        if (targetPlayer != null) {
            MutableComponent kickMessage = LocalizationHelper.getMessage("instaff.punishment.tempbanned",
                    reason, staffName, formattedDuration);
            targetPlayer.connection.disconnect(kickMessage);
        }

        MutableComponent successMsg = LocalizationHelper.getPrefixedMessage("instaff.command.tempban.success", targetName, formattedDuration, reason);
        announce(source, server, successMsg);
        return 1;
    }

    // -------------------------------------------------------------
    // /unban <player>
    // -------------------------------------------------------------
    private static void registerUnban(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("unban")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> {
                            String targetName = StringArgumentType.getString(ctx, "player");
                            CommandSourceStack source = ctx.getSource();
                            UUID targetUUID = PlayerResolver.resolveUUID(source.getServer(), targetName);

                            boolean unbanned = PunishmentManager.getInstance().unban(targetUUID, getStaffUUID(source), getStaffName(source), "Unbanned by staff");
                            if (unbanned) {
                                source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.unban.success", targetName), true);
                                return 1;
                            } else {
                                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.unban.not_banned", targetName));
                                return 0;
                            }
                        })));
    }

    // -------------------------------------------------------------
    // /mute <player> [reason]
    // -------------------------------------------------------------
    private static void registerMute(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mute")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> executeMute(ctx.getSource(), StringArgumentType.getString(ctx, "player"), DEFAULT_MUTE_REASON))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeMute(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason"))))));
    }

    private static int executeMute(CommandSourceStack source, String targetName, String reason) {
        MinecraftServer server = source.getServer();
        UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);

        UUID staffUUID = getStaffUUID(source);
        String staffName = getStaffName(source);

        PunishmentRecord record = new PunishmentRecord(
                targetUUID, targetName, staffUUID, staffName,
                PunishmentType.MUTE, reason, -1L, null, null
        );
        PunishmentManager.getInstance().addPunishment(record);

        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.muted",
                    LocalizationHelper.getRawTranslation("instaff.common.permanent"), reason));
        }

        MutableComponent successMsg = LocalizationHelper.getPrefixedMessage("instaff.command.mute.success", targetName, reason);
        announce(source, server, successMsg);
        return 1;
    }

    // -------------------------------------------------------------
    // /tempmute <player> <duration> [reason]
    // -------------------------------------------------------------
    private static void registerTempMute(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tempmute")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"15m", "30m", "1h", "2h", "1d"}, builder))
                                .executes(ctx -> executeTempMute(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "duration"), DEFAULT_MUTE_REASON))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeTempMute(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "duration"), StringArgumentType.getString(ctx, "reason")))))));
    }

    private static int executeTempMute(CommandSourceStack source, String targetName, String durationStr, String reason) {
        OptionalLong durationOpt = DurationParser.tryParseDurationMillis(durationStr);
        if (durationOpt.isEmpty() || durationOpt.getAsLong() <= 0) {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.error.invalid_duration", durationStr));
            return 0;
        }
        long durationMillis = durationOpt.getAsLong();

        MinecraftServer server = source.getServer();
        UUID targetUUID = PlayerResolver.resolveUUID(server, targetName);
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);

        UUID staffUUID = getStaffUUID(source);
        String staffName = getStaffName(source);

        PunishmentRecord record = new PunishmentRecord(
                targetUUID, targetName, staffUUID, staffName,
                PunishmentType.TEMP_MUTE, reason, durationMillis, null, null
        );
        PunishmentManager.getInstance().addPunishment(record);

        String formattedDuration = DurationParser.formatDuration(durationMillis);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.muted", formattedDuration, reason));
        }

        MutableComponent successMsg = LocalizationHelper.getPrefixedMessage("instaff.command.tempmute.success", targetName, formattedDuration, reason);
        announce(source, server, successMsg);
        return 1;
    }

    // -------------------------------------------------------------
    // /unmute <player>
    // -------------------------------------------------------------
    private static void registerUnmute(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("unmute")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> {
                            String targetName = StringArgumentType.getString(ctx, "player");
                            CommandSourceStack source = ctx.getSource();
                            UUID targetUUID = PlayerResolver.resolveUUID(source.getServer(), targetName);

                            boolean unmuted = PunishmentManager.getInstance().unmute(targetUUID, getStaffUUID(source), getStaffName(source), "Unmuted by staff");
                            if (unmuted) {
                                ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(targetName);
                                if (targetPlayer != null) {
                                    targetPlayer.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.unmuted"));
                                }
                                source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.unmute.success", targetName), true);
                                return 1;
                            } else {
                                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.unmute.not_muted", targetName));
                                return 0;
                            }
                        })));
    }

    // -------------------------------------------------------------
    // /kick <player> [reason]
    // -------------------------------------------------------------
    private static void registerKick(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kick")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> executeKick(ctx.getSource(), StringArgumentType.getString(ctx, "player"), DEFAULT_KICK_REASON))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeKick(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason"))))));
    }

    private static int executeKick(CommandSourceStack source, String targetName, String reason) {
        MinecraftServer server = source.getServer();
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);

        if (targetPlayer == null) {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.error.player_not_found", targetName));
            return 0;
        }

        UUID staffUUID = getStaffUUID(source);
        String staffName = getStaffName(source);

        PunishmentRecord record = new PunishmentRecord(
                targetPlayer.getUUID(), targetName, staffUUID, staffName,
                PunishmentType.KICK, reason, 0L, targetPlayer.getIpAddress(), resolveClientToken(targetPlayer.getUUID())
        );
        PunishmentManager.getInstance().addPunishment(record);

        targetPlayer.connection.disconnect(LocalizationHelper.getMessage("instaff.punishment.kicked", reason, staffName));

        MutableComponent successMsg = LocalizationHelper.getPrefixedMessage("instaff.command.kick.success", targetName, reason);
        announce(source, server, successMsg);
        return 1;
    }

    // -------------------------------------------------------------
    // /freeze <player>
    // -------------------------------------------------------------
    private static void registerFreeze(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("freeze")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> {
                            String targetName = StringArgumentType.getString(ctx, "player");
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(targetName);

                            if (targetPlayer == null) {
                                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.error.player_not_found", targetName));
                                return 0;
                            }

                            UUID targetUUID = targetPlayer.getUUID();
                            UUID staffUUID = getStaffUUID(source);
                            String staffName = getStaffName(source);

                            boolean isCurrentlyFrozen = PunishmentManager.getInstance().isFrozen(targetUUID);
                            if (isCurrentlyFrozen) {
                                PunishmentManager.getInstance().setFrozen(targetUUID, targetName, staffUUID, staffName, false, "Unfrozen by staff");
                                targetPlayer.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.unfrozen"));
                                source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.freeze.off", targetName), true);
                            } else {
                                PunishmentManager.getInstance().setFrozen(targetUUID, targetName, staffUUID, staffName, true, "Frozen by staff");
                                targetPlayer.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.punishment.frozen"));
                                source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.freeze.on", targetName), true);
                            }
                            return 1;
                        })));
    }

    // -------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------
    private static UUID getStaffUUID(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer staff) {
            return staff.getUUID();
        }
        return null;
    }

    private static String getStaffName(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer staff) {
            return staff.getGameProfile().getName();
        }
        return "CONSOLE";
    }

    /**
     * Resolves the client installation token bound to a player, so that bans can be matched
     * against it during the integrity handshake of a future connection (ban evasion deterrence).
     * Falls back to the most recent token stored in the punishment history.
     */
    @Nullable
    private static String resolveClientToken(UUID targetUUID) {
        String token = ServerIntegrityValidator.getInstance().getKnownClientToken(targetUUID);
        if (token != null && !token.isBlank()) {
            return token;
        }
        return PunishmentManager.getInstance().findLastKnownClientToken(targetUUID);
    }

    /**
     * Announces a punishment exactly once per recipient.
     * When broadcasting is enabled the message goes to every player (the issuer via command
     * feedback, everyone else via a system message); operator command feedback is suppressed
     * in that case so staff do not receive the same announcement twice.
     */
    private static void announce(CommandSourceStack source, MinecraftServer server, MutableComponent message) {
        boolean broadcast = InStaffConfig.isBroadcastPunishments();
        source.sendSuccess(() -> message, !broadcast);

        if (broadcast) {
            Entity issuer = source.getEntity();
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online != issuer) {
                    online.sendSystemMessage(message);
                }
            }
        }

        InStaff.LOGGER.info("[Audit] {}", message.getString());
    }
}
