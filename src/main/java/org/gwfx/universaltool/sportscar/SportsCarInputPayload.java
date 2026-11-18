package org.gwfx.universaltool.sportscar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * 海湾 GT 跑车客户端发往服务端的驾驶控制数据包。
 */
public record SportsCarInputPayload(
        float throttle,
        float steerOverride,
        float targetYaw,
        boolean handbrake,
        boolean nitro
) implements CustomPacketPayload {

    public static final Type<SportsCarInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "sports_car_input"));

    public static final StreamCodec<ByteBuf, SportsCarInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SportsCarInputPayload::throttle,
            ByteBufCodecs.FLOAT, SportsCarInputPayload::steerOverride,
            ByteBufCodecs.FLOAT, SportsCarInputPayload::targetYaw,
            ByteBufCodecs.BOOL, SportsCarInputPayload::handbrake,
            ByteBufCodecs.BOOL, SportsCarInputPayload::nitro,
            SportsCarInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端校验骑乘身份（仅主驾驶位 0 号位可操控车辆）并下发清洗后的输入状态。
     */
    public static void handle(SportsCarInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof SportsCarEntity car && car.isDriver(context.player())) {
                float safeThrottle = Float.isFinite(payload.throttle())
                        ? Mth.clamp(payload.throttle(), -1.0F, 1.0F) : 0.0F;
                float safeSteer = Float.isFinite(payload.steerOverride())
                        ? Mth.clamp(payload.steerOverride(), -1.0F, 1.0F) : 0.0F;
                float safeYaw = Float.isFinite(payload.targetYaw())
                        ? Mth.wrapDegrees(payload.targetYaw()) : car.getCarYaw();
                car.setServerInput(safeThrottle, safeSteer, safeYaw, payload.handbrake(), payload.nitro());
            }
        });
    }
}
