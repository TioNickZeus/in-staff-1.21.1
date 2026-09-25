package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import com.tio.instaff.access.MaintenanceManager;
import com.tio.instaff.access.WhitelistManager;
import com.tio.instaff.util.LocalizationHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Configuration task executed during the network configuration phase before the player enters the world.
 * Intercepts unauthorized players early when Maintenance mode or Smart Whitelist is active,
 * preventing world spawning and join broadcasts.
 */
public class AccessCheckConfigurationTask implements ConfigurationTask {

    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(
            ResourceLocation.fromNamespaceAndPath(InStaff.MODID, "access_check")
    );

    private final ServerConfigurationPacketListenerImpl listener;

    public AccessCheckConfigurationTask(ServerConfigurationPacketListenerImpl listener) {
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
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

            boolean isStaff = false;
            if (server != null) {
                isStaff = server.getPlayerList().isOp(this.listener.getOwner())
                        || server.getProfilePermissions(this.listener.getOwner()) >= 2;
            }

            // 1. Check Maintenance Mode
            if (!MaintenanceManager.getInstance().isAllowed(uuid, isStaff)) {
                InStaff.LOGGER.info("Rejecting player {} ({}) during configuration phase: server is under maintenance.", name, uuid);
                MutableComponent kickMessage = LocalizationHelper.getMessage("instaff.maintenance.kick_message");
                this.listener.disconnect(kickMessage);
                return;
            }

            // 2. Check Smart Whitelist
            if (WhitelistManager.getInstance().isEnabled() && !isStaff) {
                if (!WhitelistManager.getInstance().isWhitelisted(uuid)) {
                    InStaff.LOGGER.info("Rejecting player {} ({}) during configuration phase: not on smart whitelist.", name, uuid);
                    MutableComponent kickMessage = LocalizationHelper.getMessage("instaff.whitelist.kick_message");
                    this.listener.disconnect(kickMessage);
                    return;
                }
            }

            // Player is allowed access: signal task completion to proceed with connection
            this.listener.finishCurrentTask(TYPE);
        } catch (Throwable t) {
            String ownerName = (this.listener != null && this.listener.getOwner() != null)
                    ? this.listener.getOwner().getName()
                    : "unknown";
            InStaff.LOGGER.error("[In-Staff] Unexpected error validating access control during configuration phase for: " + ownerName, t);
            // Fail safe by availability: finish task so player connection is not hung indefinitely
            if (this.listener != null) {
                try {
                    this.listener.finishCurrentTask(TYPE);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
