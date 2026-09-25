package com.tio.instaff.network;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

/**
 * Registers server configuration tasks during the network negotiation phase.
 * Registered via mod event bus.
 */
public final class ConfigurationTaskHandler {

    private ConfigurationTaskHandler() {
    }

    public static void onRegisterConfigurationTasks(RegisterConfigurationTasksEvent event) {
        if (event.getListener() instanceof ServerConfigurationPacketListenerImpl listenerImpl) {
            event.register(new PunishmentCheckConfigurationTask(listenerImpl));
            event.register(new AccessCheckConfigurationTask(listenerImpl));
        }
    }
}
