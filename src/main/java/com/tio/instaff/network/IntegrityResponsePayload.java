package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client -> Server payload returning client mod hashes, loaded mod IDs, and installation token.
 */
public record IntegrityResponsePayload(
        String clientToken,
        Map<String, String> modHashes,
        List<String> loadedModIds
) implements CustomPacketPayload {

    public static final Type<IntegrityResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InStaff.MODID, "integrity_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntegrityResponsePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), IntegrityResponsePayload::new);

    public IntegrityResponsePayload(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                readStringMap(buf),
                readStringList(buf)
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.clientToken != null ? this.clientToken : "");
        writeStringMap(buf, this.modHashes);
        writeStringList(buf, this.loadedModIds);
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

    private static Map<String, String> readStringMap(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<String, String> map = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            map.put(buf.readUtf(), buf.readUtf());
        }
        return map;
    }

    private static void writeStringMap(RegistryFriendlyByteBuf buf, Map<String, String> map) {
        if (map == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }
}
