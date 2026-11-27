package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModAttachments;
import org.gwfx.universaltool.init.ModItems;

/**
 * 客户端在贪吃蛇小游戏得分首次达到 500 分时发往服务端的奖励申领包。
 * <p>服务端严格校验分数阈值与玩家持久化领取标记（{@link ModAttachments#SNAKE_QINCHEN_CLAIMED}），
 * 每名玩家仅可领取 1 把「侵晨」，背包满时安全掉落于玩家脚下。</p>
 */
public record SnakeScoreRewardPayload(int score) implements CustomPacketPayload {

    /** 解锁「侵晨」所需的贪吃蛇分数阈值 */
    public static final int REQUIRED_SCORE = 500;

    public static final Type<SnakeScoreRewardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "snake_score_reward")
    );

    public static final StreamCodec<ByteBuf, SnakeScoreRewardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SnakeScoreRewardPayload::score,
            SnakeScoreRewardPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SnakeScoreRewardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                tryGrantReward(player, payload.score());
            }
        });
    }

    /**
     * 服务端校验并向玩家发放 1 把「侵晨」。
     *
     * @return 若本次成功发放则返回 {@code true}；未达分、已领取或状态异常返回 {@code false}。
     */
    public static boolean tryGrantReward(ServerPlayer player, int score) {
        if (player == null || !player.isAlive() || player.isSpectator() || score < REQUIRED_SCORE) {
            return false;
        }
        if (Boolean.TRUE.equals(player.getData(ModAttachments.SNAKE_QINCHEN_CLAIMED))) {
            return false;
        }
        player.setData(ModAttachments.SNAKE_QINCHEN_CLAIMED, true);

        ItemStack sword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        if (!player.getInventory().add(sword)) {
            player.drop(sword, false);
        }

        player.serverLevel().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS,
                0.9F,
                1.0F
        );
        player.serverLevel().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.75F,
                1.15F
        );
        player.sendSystemMessage(
                Component.translatable("message.universal_tool.snake.qinchen_reward", REQUIRED_SCORE)
        );
        return true;
    }
}
