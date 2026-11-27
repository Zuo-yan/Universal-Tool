package org.gwfx.universaltool.snake;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModAttachments;
import org.gwfx.universaltool.init.ModItems;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.gwfx.universaltool.snake.SnakeBoard.REWARD_TARGET_SCORE;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class SnakeSessions {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SnakeSessions() {}

    static void input(ServerPlayer player, SnakeInputPayload input) {
        if (!eligible(player)) return;
        UUID id = player.getUUID();
        Session session = SESSIONS.get(id);
        if (input.action() == SnakeInputPayload.Action.OPEN) {
            if (session == null || session.pageId != input.pageId()) {
                int best = session == null ? 0 : session.game.bestScore();
                session = new Session(input.pageId(), new SnakeGame(new Random(), best));
                SESSIONS.put(id, session);
            }
            session.game.open();
            session.open = true;
            session.sync(player);
        } else if (session != null && session.pageId == input.pageId() && session.open) {
            session.input(player, input.action());
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null) {
                if (eligible(player)) session.tick(player);
                else if (session.game.pause()) session.sync(player);
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        SESSIONS.clear();
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator();
    }

    static final class Session {
        private final int pageId;
        private final SnakeGame game;
        private boolean open = true;

        Session(int pageId, SnakeGame game) {
            this.pageId = pageId;
            this.game = game;
        }

        void input(ServerPlayer player, SnakeInputPayload.Action action) {
            boolean changed = switch (action) {
                case OPEN -> false;
                case CLOSE -> {
                    game.pause();
                    open = false;
                    yield false;
                }
                case PAUSE -> game.pause();
                case TOGGLE -> {
                    game.toggle();
                    yield true;
                }
                case RESTART -> {
                    game.restart();
                    yield true;
                }
                case UP -> game.turn(SnakeBoard.Dir.UP);
                case DOWN -> game.turn(SnakeBoard.Dir.DOWN);
                case LEFT -> game.turn(SnakeBoard.Dir.LEFT);
                case RIGHT -> game.turn(SnakeBoard.Dir.RIGHT);
            };
            if (changed) sync(player);
        }

        void tick(ServerPlayer player) {
            if (!open) return;
            int previousScore = game.score();
            if (!game.tick()) return;
            // Award only on a server-simulated scoring movement.
            if (game.score() > previousScore && game.score() >= REWARD_TARGET_SCORE) {
                award(player);
            }
            sync(player);
        }

        void sync(ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SnakeStatePayload(pageId, game.snapshot()));
        }
    }

    private static void award(ServerPlayer player) {
        if (player.getData(ModAttachments.SNAKE_QINCHEN_CLAIMED)) return;
        ItemStack sword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        if (player.getInventory().getFreeSlot() < 0 || !player.getInventory().add(sword)) {
            if (player.drop(sword, false) == null) return;
        }
        player.setData(ModAttachments.SNAKE_QINCHEN_CLAIMED, true);

        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.15F);
        player.sendSystemMessage(Component.translatable("message.universal_tool.snake.qinchen_reward", REWARD_TARGET_SCORE));
    }
}
