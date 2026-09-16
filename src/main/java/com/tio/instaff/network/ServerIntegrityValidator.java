package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import com.tio.instaff.config.InStaffConfig;
import com.tio.instaff.moderation.PunishmentManager;
import com.tio.instaff.moderation.PunishmentRecord;
import com.tio.instaff.util.DurationParser;
import com.tio.instaff.util.LocalizationHelper;
import net.minecraft.Util;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side validator for client integrity handshakes and ban evasion detection.
 */
public final class ServerIntegrityValidator {

    private static final ServerIntegrityValidator INSTANCE = new ServerIntegrityValidator();
    private final Map<UUID, Long> pendingVerifications = new ConcurrentHashMap<>();
    private final Set<UUID> verifiedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Last known client installation token per player UUID.
     * Deliberately retained after logout so that banning a player who just disconnected
     * still binds the ban to their installation token.
     */
    private final Map<UUID, String> knownClientTokens = new ConcurrentHashMap<>();

    private ServerIntegrityValidator() {
    }

    public static ServerIntegrityValidator getInstance() {
        return INSTANCE;
    }

    /**
     * Initiates the integrity verification handshake for a connecting player.
     */
    public void initiateHandshake(@NotNull ServerPlayer player) {
        if (!InStaffConfig.isIntegrityEnabled()) {
            verifiedPlayers.add(player.getUUID());
            return;
        }

        pendingVerifications.put(player.getUUID(), Util.getMillis());

        IntegrityRequestPayload request = new IntegrityRequestPayload(
                InStaffConfig.getIntegrityTimeoutSeconds(),
                new ArrayList<>(InStaffConfig.getRequiredMods()),
                new ArrayList<>(InStaffConfig.getBlacklistedModIds()),
                new ArrayList<>(InStaffConfig.getBlacklistedHashes())
        );

        PacketDistributor.sendToPlayer(player, request);
        InStaff.LOGGER.info("Sent integrity verification request to player {} ({})",
                player.getGameProfile().getName(), player.getUUID());
    }

    /**
     * Validates the client's integrity response against server moderation rules and mod lists.
     */
    public void handleResponse(@NotNull ServerPlayer player, @NotNull IntegrityResponsePayload response) {
        UUID uuid = player.getUUID();
        pendingVerifications.remove(uuid);

        String token = response.clientToken();
        if (token != null && !token.isBlank()) {
            knownClientTokens.put(uuid, token);
        }
        InStaff.LOGGER.info("Processing integrity response for player {} ({}) with installation token {}",
                player.getGameProfile().getName(), uuid, token);

        // 1. Offline Ban Evasion check: token match against active bans
        if (token != null && !token.isBlank() && InStaffConfig.isPreventOfflineBanEvasion()) {
            Optional<PunishmentRecord> activeBan = PunishmentManager.getInstance().getActiveBanByClientToken(token);
            if (activeBan.isPresent()) {
                PunishmentRecord ban = activeBan.get();
                InStaff.LOGGER.warn("Player {} ({}) detected evading ban with matching client installation token {}",
                        player.getGameProfile().getName(), uuid, token);

                MutableComponent kickMessage;
                if (ban.getExpiresAtEpoch() == -1L) {
                    kickMessage = LocalizationHelper.getMessage("instaff.punishment.banned",
                            ban.getReason(), ban.getStaffName(), LocalizationHelper.getRawTranslation("instaff.common.permanent"));
                } else {
                    kickMessage = LocalizationHelper.getMessage("instaff.punishment.tempbanned",
                            ban.getReason(), ban.getStaffName(), DurationParser.formatRemaining(ban.getExpiresAtEpoch()));
                }
                player.connection.disconnect(kickMessage);
                return;
            }
        }

        // 2. Check Required Mods
        List<? extends String> requiredMods = InStaffConfig.getRequiredMods();
        List<String> clientMods = response.loadedModIds() != null ? response.loadedModIds() : Collections.emptyList();
        for (String required : requiredMods) {
            if (!clientMods.contains(required.toLowerCase().trim())) {
                InStaff.LOGGER.warn("Player {} ({}) rejected: missing required mod '{}'",
                        player.getGameProfile().getName(), uuid, required);
                player.connection.disconnect(LocalizationHelper.getMessage("instaff.integrity.kick_missing_mods"));
                return;
            }
        }

        // 3. Check Blacklisted Mod IDs
        List<? extends String> blacklistedModIds = InStaffConfig.getBlacklistedModIds();
        for (String blacklisted : blacklistedModIds) {
            if (clientMods.contains(blacklisted.toLowerCase().trim())) {
                InStaff.LOGGER.warn("Player {} ({}) rejected: unauthorized mod ID '{}' detected",
                        player.getGameProfile().getName(), uuid, blacklisted);
                player.connection.disconnect(LocalizationHelper.getMessage("instaff.integrity.kick_blacklisted_mods", blacklisted));
                return;
            }
        }

        // 4. Check Blacklisted Hashes
        List<? extends String> blacklistedHashes = InStaffConfig.getBlacklistedHashes();
        if (response.modHashes() != null && !blacklistedHashes.isEmpty()) {
            for (Map.Entry<String, String> entry : response.modHashes().entrySet()) {
                String hash = entry.getValue();
                if (blacklistedHashes.contains(hash.toLowerCase().trim())) {
                    InStaff.LOGGER.warn("Player {} ({}) rejected: forbidden mod hash '{}' detected ({})",
                            player.getGameProfile().getName(), uuid, hash, entry.getKey());
                    player.connection.disconnect(LocalizationHelper.getMessage("instaff.integrity.kick_blacklisted_mods", entry.getKey()));
                    return;
                }
            }
        }

        // Handshake Passed
        verifiedPlayers.add(uuid);
        InStaff.LOGGER.info("Player {} ({}) successfully passed client integrity verification.",
                player.getGameProfile().getName(), uuid);
    }

    /**
     * Periodic watchdog tick verifying that clients do not exceed the handshake timeout.
     */
    public void tickWatchdog(@NotNull ServerPlayer player) {
        if (!InStaffConfig.isIntegrityEnabled()) {
            return;
        }

        UUID uuid = player.getUUID();
        Long requestTime = pendingVerifications.get(uuid);
        if (requestTime != null) {
            long elapsed = Util.getMillis() - requestTime;
            long timeoutMillis = InStaffConfig.getIntegrityTimeoutSeconds() * 1000L;

            if (elapsed > timeoutMillis) {
                pendingVerifications.remove(uuid);
                InStaff.LOGGER.warn("Player {} ({}) kicked: integrity handshake timed out (elapsed {}ms)",
                        player.getGameProfile().getName(), uuid, elapsed);
                player.connection.disconnect(LocalizationHelper.getMessage("instaff.integrity.kick_timeout"));
            }
        }
    }

    public void onPlayerLeave(@NotNull UUID uuid) {
        pendingVerifications.remove(uuid);
        verifiedPlayers.remove(uuid);
        // knownClientTokens is intentionally preserved so that /ban issued right after a
        // disconnect can still bind the ban to the player's installation token.
    }

    /**
     * Returns the last client installation token reported by this player during an integrity
     * handshake, or {@code null} when the token is unknown (handshake disabled, never connected
     * since the last server restart, or client did not report one).
     */
    @Nullable
    public String getKnownClientToken(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return knownClientTokens.get(uuid);
    }

    public boolean isVerified(@NotNull UUID uuid) {
        return !InStaffConfig.isIntegrityEnabled() || verifiedPlayers.contains(uuid);
    }
}
