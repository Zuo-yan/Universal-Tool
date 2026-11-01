package org.gwfx.universaltool.lottery;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record LotteryRollActionPayload(int mode, int betCount) implements CustomPacketPayload {

    public static final Type<LotteryRollActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "lottery_roll"));

    public static final StreamCodec<ByteBuf, LotteryRollActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LotteryRollActionPayload::mode,
            ByteBufCodecs.VAR_INT, LotteryRollActionPayload::betCount,
            LotteryRollActionPayload::new
    );

    public LotteryRollActionPayload() {
        this(0, 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LotteryRollActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof UniversalLotteryMachineMenu menu) {
                menu.handleRollButton(player, payload.mode(), payload.betCount());
            }
        });
    }
}
