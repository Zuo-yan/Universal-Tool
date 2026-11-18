package org.gwfx.universaltool.wheelchair;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Client-to-server driving input payload for {@link AllTerrainWheelchairEntity}.
 */
public record WheelchairInputPayload(
        float throttle,
        float steer,
        boolean jumping,
        boolean boosting
) implements CustomPacketPayload {

    public static final Type<WheelchairInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "wheelchair_input"));

    public static final StreamCodec<ByteBuf, WheelchairInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, WheelchairInputPayload::throttle,
            ByteBufCodecs.FLOAT, WheelchairInputPayload::steer,
            ByteBufCodecs.BOOL, WheelchairInputPayload::jumping,
            ByteBufCodecs.BOOL, WheelchairInputPayload::boosting,
            WheelchairInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WheelchairInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof AllTerrainWheelchairEntity wc) {
                float safeThrottle = Float.isFinite(payload.throttle())
                        ? Mth.clamp(payload.throttle(), -1.0F, 1.0F) : 0.0F;
                float safeSteer = Float.isFinite(payload.steer())
                        ? Mth.clamp(payload.steer(), -1.0F, 1.0F) : 0.0F;
                wc.setServerInput(safeThrottle, safeSteer, payload.jumping(), payload.boosting());
            }
        });
    }
}
