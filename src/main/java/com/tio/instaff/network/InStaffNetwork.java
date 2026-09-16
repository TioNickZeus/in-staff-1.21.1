package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Network registration and dispatch for In-Staff payloads.
 * Maintains strict side separation by decoupling client-side packet handling
 * through a functional delegate injected by InStaffClient.
 */
public final class InStaffNetwork {

    public static final String PROTOCOL_VERSION = "1.0.0";

    @Nullable
    public static Consumer<IntegrityRequestPayload> clientRequestHandler = null;

    private InStaffNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, InStaffNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Server -> Client: Integrity Verification Request
        registrar.playToClient(
                IntegrityRequestPayload.TYPE,
                IntegrityRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (clientRequestHandler != null) {
                        clientRequestHandler.accept(payload);
                    }
                }
        );

        // Client -> Server: Integrity Verification Response
        registrar.playToServer(
                IntegrityResponsePayload.TYPE,
                IntegrityResponsePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        ServerIntegrityValidator.getInstance().handleResponse(serverPlayer, payload);
                    }
                }
        );

        InStaff.LOGGER.info("In-Staff network payloads registered (protocol version {})", PROTOCOL_VERSION);
    }
}
