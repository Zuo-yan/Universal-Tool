package org.gwfx.universaltool.snake;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.init.ModAttachments;
import org.gwfx.universaltool.init.ModItems;

import java.util.Random;
import java.util.UUID;

import static org.gwfx.universaltool.snake.SnakeBoard.REWARD_TARGET_SCORE;

@PrefixGameTestTemplate(false)
public final class SnakeGameTests {
    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void rewardRequiresServerSimulatedPlayAndIsGrantedOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "snake-reward-test");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(server, connection, player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
        };

        try {
            SnakeGame game = new SnakeGame(new Random(42), 0);
            SnakeSessions.Session session = new SnakeSessions.Session(1, game);
            session.input(player, SnakeInputPayload.Action.TOGGLE);
            for (int i = 0; i < 30_000 && game.score() < 250; i++) {
                SnakeBoard board = game.snapshot();
                helper.assertTrue(board.state() == SnakeBoard.State.PLAYING, "Snake must stay on the board");
                if (board.score() < REWARD_TARGET_SCORE) {
                    helper.assertTrue(!player.getData(ModAttachments.SNAKE_QINCHEN_CLAIMED),
                            "Reward must not be claimed under the threshold");
                }
                session.input(player, nextTurn(board.snake().getFirst()));
                session.tick(player);
                if (game.score() >= REWARD_TARGET_SCORE) {
                    helper.assertTrue(player.getData(ModAttachments.SNAKE_QINCHEN_CLAIMED),
                            "A server-simulated threshold crossing must award the sword");
                }
            }
            helper.assertTrue(game.score() >= 250, "The simulated run must keep scoring after the threshold");
            helper.assertTrue(player.getInventory().countItem(ModItems.QINCHEN_SWORD.get()) == 1,
                    "Further scoring must not award another sword");
            helper.succeed();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    // Traverse the full grid using column zero as the return lane.
    private static SnakeInputPayload.Action nextTurn(SnakeBoard.Cell head) {
        if (head.x() == 0) return head.y() == 13 ? SnakeInputPayload.Action.RIGHT : SnakeInputPayload.Action.DOWN;
        if ((head.y() & 1) != 0) return head.x() == 13 ? SnakeInputPayload.Action.UP : SnakeInputPayload.Action.RIGHT;
        if (head.x() == 1 && head.y() > 0) return SnakeInputPayload.Action.UP;
        return SnakeInputPayload.Action.LEFT;
    }
}
