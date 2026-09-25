package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import com.tio.instaff.moderation.PunishmentManager;
import com.tio.instaff.moderation.PunishmentRecord;
import com.tio.instaff.util.DurationParser;
import com.tio.instaff.util.LocalizationHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Configuration task executed during the network configuration phase before player joins world.
 * Intercepts banned players early, preventing world spawning and join broadcasts.
 */
public class PunishmentCheckConfigurationTask implements ConfigurationTask {

    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(
            ResourceLocation.fromNamespaceAndPath(InStaff.MODID, "punishment_check")
    );

    private final ServerConfigurationPacketListenerImpl listener;

    public PunishmentCheckConfigurationTask(ServerConfigurationPacketListenerImpl listener) {
        this.listener = listener;
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        try {
            UUID uuid = this.listener.getOwner().getId();
            String name = this.listener.getOwner().getName();

            // Resolve IP address safely
            String ipAddress = null;
            SocketAddress remoteAddress = this.listener.getConnection().getRemoteAddress();
            if (remoteAddress instanceof InetSocketAddress inet) {
                ipAddress = inet.getAddress().getHostAddress();
            }

            // Check active ban by UUID
            Optional<PunishmentRecord> activeBan = PunishmentManager.getInstance().getActiveBan(uuid);

            // Check active ban by IP (offline ban evasion check)
            if (activeBan.isEmpty() && ipAddress != null) {
                activeBan = PunishmentManager.getInstance().getActiveBanByIp(ipAddress);
            }

            if (activeBan.isPresent()) {
                PunishmentRecord ban = activeBan.get();
                MutableComponent kickMessage;
                if (ban.getExpiresAtEpoch() == -1L) {
                    kickMessage = LocalizationHelper.getMessage("instaff.punishment.banned",
                            ban.getReason(), ban.getStaffName(), LocalizationHelper.getRawTranslation("instaff.common.permanent"));
                } else {
                    kickMessage = LocalizationHelper.getMessage("instaff.punishment.tempbanned",
                            ban.getReason(), ban.getStaffName(), DurationParser.formatDuration(ban.getRemainingMillis()));
                }
                InStaff.LOGGER.info("Rejecting banned player {} ({}) during configuration phase: {}", name, uuid, ban.getReason());
                this.listener.disconnect(kickMessage);
                return;
            }

            // Player is not banned: signal task completion to proceed with connection
            this.listener.finishCurrentTask(TYPE);
        } catch (Throwable t) {
            InStaff.LOGGER.error("[In-Staff] Unexpected error validating punishment during configuration phase for: " + this.listener.getOwner().getName(), t);
            // Fail safe by availability: finish task so player connection is not hung indefinitely
            this.listener.finishCurrentTask(TYPE);
        }
    }
}
