package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client payload initiating client integrity verification.
 * Specifies required mods, blacklisted mod IDs, blacklisted hashes, and timeout.
 */
public record IntegrityRequestPayload(
        int timeoutSeconds,
        List<String> requiredMods,
        List<String> blacklistedModIds,
        List<String> blacklistedHashes
) implements CustomPacketPayload {

    public static final Type<IntegrityRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InStaff.MODID, "integrity_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntegrityRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), IntegrityRequestPayload::new);

    public IntegrityRequestPayload(RegistryFriendlyByteBuf buf) {
        this(
                buf.readVarInt(),
                readStringList(buf),
                readStringList(buf),
                readStringList(buf)
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.timeoutSeconds);
        writeStringList(buf, this.requiredMods);
        writeStringList(buf, this.blacklistedModIds);
        writeStringList(buf, this.blacklistedHashes);
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> list) {
        if (list == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(list.size());
        for (String s : list) {
            buf.writeUtf(s);
        }
    }
}
