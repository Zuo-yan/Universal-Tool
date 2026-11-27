package org.gwfx.universaltool.snake;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record SnakeInputPayload(int pageId, Action action) implements CustomPacketPayload {
    public enum Action { OPEN, CLOSE, PAUSE, TOGGLE, RESTART, UP, DOWN, LEFT, RIGHT }

    public static final Type<SnakeInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "snake_input"));
    public static final StreamCodec<FriendlyByteBuf, SnakeInputPayload> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.pageId);
                buf.writeEnum(packet.action);
            }, buf -> new SnakeInputPayload(buf.readVarInt(), buf.readEnum(Action.class)));

    @Override
    public Type<SnakeInputPayload> type() {
        return TYPE;
    }

    public static void handle(SnakeInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) SnakeSessions.input(player, payload);
        });
    }
}
