package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.PersonalDimensionClientRegistry;

public record PersonalDimensionSyncPayload(ResourceLocation id, CompoundTag serializedType, boolean remove)
        implements CustomPacketPayload {
    public static final Type<PersonalDimensionSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "personal_dimension_sync"));
    public static final StreamCodec<ByteBuf, PersonalDimensionSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PersonalDimensionSyncPayload::id,
            ByteBufCodecs.COMPOUND_TAG, PersonalDimensionSyncPayload::serializedType,
            ByteBufCodecs.BOOL, PersonalDimensionSyncPayload::remove,
            PersonalDimensionSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PersonalDimensionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PersonalDimensionClientRegistry.apply(payload));
    }
}
