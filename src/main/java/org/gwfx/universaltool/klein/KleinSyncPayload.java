package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record KleinSyncPayload(List<KleinEntryData> entries, boolean reset, boolean complete) implements CustomPacketPayload {
    public KleinSyncPayload(List<KleinEntryData> entries, boolean reset) {
        this(entries, reset, true);
    }
    public static final Type<KleinSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KleinSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public KleinSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            int size = ByteBufCodecs.VAR_INT.decode(buffer);
            if (size < 0 || size > 64) throw new IllegalArgumentException("Invalid Klein sync page");
            List<KleinEntryData> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) entries.add(KleinEntryData.STREAM_CODEC.decode(buffer));
            return new KleinSyncPayload(entries, buffer.readBoolean(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, KleinSyncPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.entries.size());
            value.entries.forEach(entry -> KleinEntryData.STREAM_CODEC.encode(buffer, entry));
            buffer.writeBoolean(value.reset);
            buffer.writeBoolean(value.complete);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(KleinSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof KleinMenu menu) menu.applySync(payload);
        });
    }
}
