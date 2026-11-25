package org.gwfx.universaltool.phainon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/** Vanilla sends no attack event on an air swing. The server validates the awakened form and movement. */
public record PhainonEmptySwingPayload() implements CustomPacketPayload {
    public static final PhainonEmptySwingPayload INSTANCE = new PhainonEmptySwingPayload();
    public static final Type<PhainonEmptySwingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "phainon_empty_swing"));
    public static final StreamCodec<ByteBuf, PhainonEmptySwingPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<PhainonEmptySwingPayload> type() {
        return TYPE;
    }

    public static void handle(PhainonEmptySwingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer caster) {
                PhainonUltimateHandler.cueEnhancedBasic(caster, null);
            }
        });
    }
}
