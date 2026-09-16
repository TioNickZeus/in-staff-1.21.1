package com.tio.instaff.network;

import com.tio.instaff.InStaff;
import io.netty.handler.codec.DecoderException;
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

    /** Hard caps on untrusted, client-supplied collection sizes (see readStringList/readStringMap). */
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_STRING_LENGTH = 512;

    public static final Type<IntegrityResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InStaff.MODID, "integrity_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntegrityResponsePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), IntegrityResponsePayload::new);

    public IntegrityResponsePayload(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUtf(MAX_STRING_LENGTH),
                readStringMap(buf),
                readStringList(buf)
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.clientToken != null ? this.clientToken : "", MAX_STRING_LENGTH);
        writeStringMap(buf, this.modHashes);
        writeStringList(buf, this.loadedModIds);
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf) {
        // The count comes from an untrusted client: never pre-allocate based on it directly.
        int count = readBoundedCount(buf);
        List<String> list = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            list.add(buf.readUtf(MAX_STRING_LENGTH));
        }
        return list;
    }

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> list) {
        if (list == null) {
            buf.writeVarInt(0);
            return;
        }
        int count = Math.min(list.size(), MAX_ENTRIES);
        if (count < list.size()) {
            InStaff.LOGGER.warn("Integrity response truncated from {} to {} entries", list.size(), count);
        }
        buf.writeVarInt(count);
        int written = 0;
        for (String s : list) {
            if (written++ >= count) {
                break;
            }
            buf.writeUtf(truncate(s), MAX_STRING_LENGTH);
        }
    }

    private static Map<String, String> readStringMap(RegistryFriendlyByteBuf buf) {
        // The count comes from an untrusted client: never pre-allocate based on it directly.
        int count = readBoundedCount(buf);
        Map<String, String> map = new HashMap<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            map.put(buf.readUtf(MAX_STRING_LENGTH), buf.readUtf(MAX_STRING_LENGTH));
        }
        return map;
    }

    /**
     * Reads an element count and rejects values that are negative, above {@link #MAX_ENTRIES},
     * or larger than the bytes actually remaining in the buffer. This prevents a malicious client
     * from forcing a huge allocation with a tiny packet.
     */
    private static int readBoundedCount(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES || count > buf.readableBytes()) {
            throw new DecoderException("Invalid In-Staff integrity payload element count: " + count);
        }
        return count;
    }

    private static void writeStringMap(RegistryFriendlyByteBuf buf, Map<String, String> map) {
        if (map == null) {
            buf.writeVarInt(0);
            return;
        }
        int count = Math.min(map.size(), MAX_ENTRIES);
        if (count < map.size()) {
            InStaff.LOGGER.warn("Integrity response truncated from {} to {} hashes", map.size(), count);
        }
        buf.writeVarInt(count);
        int written = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (written++ >= count) {
                break;
            }
            buf.writeUtf(truncate(entry.getKey()), MAX_STRING_LENGTH);
            buf.writeUtf(truncate(entry.getValue()), MAX_STRING_LENGTH);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_STRING_LENGTH ? value : value.substring(0, MAX_STRING_LENGTH);
    }
}
