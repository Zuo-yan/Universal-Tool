package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.SkillManager;

/**
 * Asks the server to abort an in-flight charge.
 *
 * <p>Sent when the selection wheel or the management screen opens mid-charge, so the charge is
 * cancelled by the same authority that started it and the short cancel lockout applies.
 */
public record CancelChargePayload() implements CustomPacketPayload {
    public static final CancelChargePayload INSTANCE = new CancelChargePayload();
    public static final Type<CancelChargePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "cancel_charge"));
    public static final StreamCodec<ByteBuf, CancelChargePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CancelChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillManager.cancelCharge(player);
            }
        });
    }
}
