package com.tio.instaff.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolver for player identities, strictly adhering to offline-mode compatibility.
 * Prohibits external Mojang HTTP requests, resolving identities via active players,
 * the server's local GameProfileCache, or deterministic offline UUIDs.
 */
public final class PlayerResolver {

    private PlayerResolver() {
    }

    /**
     * Generates a deterministic offline UUID for a player name according to standard Minecraft specification:
     * UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).
     */
    @NotNull
    public static UUID createOfflineUUID(@NotNull String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resolves a player UUID by name using purely local mechanisms:
     * 1. Active online ServerPlayer
     * 2. Server's local GameProfileCache
     * 3. Deterministic offline player UUID fallback
     */
    @NotNull
    public static UUID resolveUUID(@Nullable MinecraftServer server, @NotNull String username) {
        if (server != null) {
            // 1. Check active server player
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(username);
            if (onlinePlayer != null) {
                return onlinePlayer.getUUID();
            }

            // 2. Check local server GameProfileCache
            GameProfileCache profileCache = server.getProfileCache();
            if (profileCache != null) {
                Optional<GameProfile> cached = profileCache.get(username);
                if (cached.isPresent() && cached.get().getId() != null) {
                    return cached.get().getId();
                }
            }
        }

        // 3. Deterministic offline UUID fallback
        return createOfflineUUID(username);
    }
}
