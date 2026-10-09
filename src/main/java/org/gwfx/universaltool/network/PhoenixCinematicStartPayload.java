package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.PhoenixCinematicController;

public record PhoenixCinematicStartPayload(BlockPos podPos, int facingIndex, String dimensionId, int totalTicks) implements CustomPacketPayload {

    public static final Type<PhoenixCinematicStartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "phoenix_cinematic_start"));

    public static final StreamCodec<ByteBuf, PhoenixCinematicStartPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PhoenixCinematicStartPayload::podPos,
            ByteBufCodecs.VAR_INT, PhoenixCinematicStartPayload::facingIndex,
            ByteBufCodecs.STRING_UTF8, PhoenixCinematicStartPayload::dimensionId,
            ByteBufCodecs.VAR_INT, PhoenixCinematicStartPayload::totalTicks,
            PhoenixCinematicStartPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PhoenixCinematicStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Direction facing = Direction.from2DDataValue(payload.facingIndex());
            PhoenixCinematicController.startCinematic(payload.podPos(), facing, payload.dimensionId(), payload.totalTicks());
        });
    }
}
