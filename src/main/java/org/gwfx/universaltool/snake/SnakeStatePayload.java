package org.gwfx.universaltool.snake;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.ArrayList;
import java.util.List;

import static org.gwfx.universaltool.snake.SnakeBoard.*;

public record SnakeStatePayload(int pageId, SnakeBoard board) implements CustomPacketPayload {
    private static final int NO_CELL = 255;
    public static final Type<SnakeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "snake_state"));
    public static final StreamCodec<FriendlyByteBuf, SnakeStatePayload> STREAM_CODEC =
            StreamCodec.of(SnakeStatePayload::write, SnakeStatePayload::read);

    @Override
    public Type<SnakeStatePayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SnakeStatePayload payload) {
        SnakeBoard board = payload.board;
        buf.writeVarInt(payload.pageId);
        buf.writeEnum(board.state());
        buf.writeEnum(board.direction());
        buf.writeByte(board.snake().size());
        for (Cell cell : board.snake()) buf.writeByte(cell.index());
        buf.writeByte(board.food() != null ? board.food().index() : NO_CELL);
        buf.writeByte(board.bonusFood() != null ? board.bonusFood().index() : NO_CELL);
        buf.writeVarInt(board.bonusTicks());
        buf.writeVarInt(board.score());
        buf.writeVarInt(board.bestScore());
    }

    private static SnakeStatePayload read(FriendlyByteBuf buf) {
        int pageId = buf.readVarInt();
        State state = buf.readEnum(State.class);
        Dir dir = buf.readEnum(Dir.class);
        int size = buf.readUnsignedByte();
        if (size > CELLS) throw new DecoderException("Snake exceeds the size of the board");
        List<Cell> snake = new ArrayList<>(size);
        for (int i = 0; i < size; i++) snake.add(readCell(buf.readUnsignedByte()));
        int foodIndex = buf.readUnsignedByte();
        int bonusIndex = buf.readUnsignedByte();
        return new SnakeStatePayload(pageId, new SnakeBoard(state, dir, snake,
                foodIndex == NO_CELL ? null : readCell(foodIndex),
                bonusIndex == NO_CELL ? null : readCell(bonusIndex),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }

    private static Cell readCell(int index) {
        if (index >= CELLS) throw new DecoderException("Invalid Snake cell " + index);
        return Cell.fromIndex(index);
    }
}
