package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/** Persists the player's pickup auto-deposit preference. */
public record KleinAutoDepositPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<KleinAutoDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_auto_deposit"));
    public static final StreamCodec<ByteBuf, KleinAutoDepositPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, KleinAutoDepositPayload::enabled,
            KleinAutoDepositPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KleinAutoDepositPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player instanceof ServerPlayer serverPlayer) {
                KleinStorageSavedData saved = KleinStorageSavedData.get(serverPlayer.server);
                saved.setAutoDeposit(serverPlayer.getUUID(), payload.enabled());
                if (serverPlayer.containerMenu instanceof KleinMenu menu) {
                    menu.applyAutoDeposit(payload.enabled());
                }
            } else if (player != null && player.containerMenu instanceof KleinMenu menu) {
                menu.applyAutoDeposit(payload.enabled());
            }
        });
    }
}
