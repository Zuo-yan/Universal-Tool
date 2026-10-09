package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.space.PersonalDimensionRegistry;

public record PersonalDimensionReadyPayload(ResourceLocation dimension) implements CustomPacketPayload {
    public static final Type<PersonalDimensionReadyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "personal_dimension_ready"));
    public static final StreamCodec<ByteBuf, PersonalDimensionReadyPayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(PersonalDimensionReadyPayload::new, PersonalDimensionReadyPayload::dimension);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PersonalDimensionReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                PersonalDimensionRegistry.from(player.server).acknowledge(player, payload.dimension());
        });
    }
}
