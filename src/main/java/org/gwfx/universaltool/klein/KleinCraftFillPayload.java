package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/** Requests a recipe from the server's recipe manager. Materials are selected on the server. */
public record KleinCraftFillPayload(int containerId, ResourceLocation recipeId, int sequence,
                                    boolean maxTransfer) implements CustomPacketPayload {
    public static final Type<KleinCraftFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_craft_fill"));

    public static final StreamCodec<ByteBuf, KleinCraftFillPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KleinCraftFillPayload::containerId,
            ResourceLocation.STREAM_CODEC, KleinCraftFillPayload::recipeId,
            ByteBufCodecs.VAR_INT, KleinCraftFillPayload::sequence,
            ByteBufCodecs.BOOL, KleinCraftFillPayload::maxTransfer,
            KleinCraftFillPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KleinCraftFillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof KleinMenu menu) {
                menu.handleCraftFill(payload);
            }
        });
    }
}
