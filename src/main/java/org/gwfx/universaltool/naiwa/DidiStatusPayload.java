package org.gwfx.universaltool.naiwa;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

public record DidiStatusPayload(int remainingTicks, int squadSize, boolean ultimateUsed,
                                String result, int resultCount) implements CustomPacketPayload {
    public static final Type<DidiStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "didi_status"));

    public static final StreamCodec<ByteBuf, DidiStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DidiStatusPayload::remainingTicks,
            ByteBufCodecs.VAR_INT, DidiStatusPayload::squadSize,
            ByteBufCodecs.BOOL, DidiStatusPayload::ultimateUsed,
            ByteBufCodecs.STRING_UTF8, DidiStatusPayload::result,
            ByteBufCodecs.VAR_INT, DidiStatusPayload::resultCount,
            DidiStatusPayload::new
    );

    @Override
    public Type<DidiStatusPayload> type() {
        return TYPE;
    }

    public Component resultMessage() {
        return Component.translatable("message.universal_tool.didi." + result, resultCount);
    }
}
