package org.gwfx.universaltool.naiwa;

import com.november.mcphone.api.cost.ICost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.init.ModEntities;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class DidiOrderService {
    private static final double FOLLOW_TELEPORT_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final ICost SINGLE_COST = ICost.of(Items.EMERALD, DidiAppPurchase.SINGLE_PRICE);
    private static final ICost SQUAD_COST = ICost.of(Items.EMERALD, DidiAppPurchase.SQUAD_PRICE);

    private DidiOrderService() {
    }

    static DidiStatusPayload execute(ServerPlayer player, int action) {
        ServerLevel level = player.serverLevel();
        DidiOrderBook book = DidiOrderBook.get(level);
        UUID owner = player.getUUID();
        DidiOrderBook.Order order = book.current(level, owner);
        Result result = switch (action) {
            case DidiOrderPayload.ACTION_STATUS -> Result.SILENT;
            case DidiOrderPayload.ACTION_SUMMON_ONE, DidiOrderPayload.ACTION_SUMMON_THREE,
                 DidiOrderPayload.ACTION_MODE_FOLLOW, DidiOrderPayload.ACTION_MODE_PATROL,
                 DidiOrderPayload.ACTION_ULTIMATE_LAUGH, DidiOrderPayload.ACTION_DISMISS_ALL ->
                    !player.isAlive() || player.isSpectator() || !DidiAppPurchase.isOwned(player)
                            ? new Result("purchase_required") : act(player, level, book, order, action);
            default -> Result.SILENT;
        };
        order = book.current(level, owner);
        return new DidiStatusPayload(order == null ? 0 : book.remaining(level, order),
                order == null ? 0 : order.size(), order != null && order.ultimateUsed(),
                result.key(), result.count());
    }

    private static Result act(ServerPlayer player, ServerLevel level, DidiOrderBook book,
                              @Nullable DidiOrderBook.Order order, int action) {
        if (action == DidiOrderPayload.ACTION_SUMMON_ONE || action == DidiOrderPayload.ACTION_SUMMON_THREE) {
            return order != null ? new Result("active_order")
                    : dispatch(player, level, book, action == DidiOrderPayload.ACTION_SUMMON_ONE ? 1 : 3);
        }
        if (order == null) {
            return new Result("none_active");
        }
        if (action == DidiOrderPayload.ACTION_DISMISS_ALL) {
            int count = order.size();
            book.finish(level, player.getUUID());
            return new Result("dismiss_all", count);
        }
        if (action == DidiOrderPayload.ACTION_ULTIMATE_LAUGH && order.ultimateUsed()) {
            return new Result("ultimate_used");
        }
        List<NaiwaEntity> squad = order.loadedMembers(level);
        if (squad.isEmpty()) {
            return new Result("none_nearby");
        }
        return switch (action) {
            case DidiOrderPayload.ACTION_MODE_FOLLOW -> {
                for (NaiwaEntity naiwa : squad) {
                    naiwa.setOrderMode(NaiwaEntity.MODE_FOLLOW);
                    if (naiwa.distanceToSqr(player) > FOLLOW_TELEPORT_DISTANCE_SQUARED) {
                        naiwa.teleportNearOwner(player);
                    }
                }
                yield new Result("mode_follow", squad.size());
            }
            case DidiOrderPayload.ACTION_MODE_PATROL -> {
                squad.forEach(naiwa -> naiwa.setOrderMode(NaiwaEntity.MODE_PATROL));
                yield new Result("mode_patrol", squad.size());
            }
            case DidiOrderPayload.ACTION_ULTIMATE_LAUGH -> {
                book.useUltimate(order);
                squad.forEach(NaiwaEntity::triggerUltimateLaugh);
                yield new Result("ultimate_laugh", squad.size());
            }
            default -> Result.SILENT;
        };
    }

    private static Result dispatch(ServerPlayer player, ServerLevel level, DidiOrderBook book, int count) {
        ICost cost = count == 1 ? SINGLE_COST : SQUAD_COST;
        int price = count == 1 ? DidiAppPurchase.SINGLE_PRICE : DidiAppPurchase.SQUAD_PRICE;
        if (!cost.canAfford(player)) {
            return new Result("cannot_afford", price);
        }

        List<NaiwaEntity> squad = new ArrayList<>(count);
        UUID orderId = UUID.randomUUID();
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        double heading = Math.atan2(look.z, look.x);
        for (int index = 0; index < count; index++) {
            double angle = heading + index * (Math.PI * 2.0 / count);
            double radius = count == 1 ? 3.0 : 3.5;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            NaiwaEntity naiwa = createAirdrop(player, level, orderId, x, z);
            if (naiwa == null || !level.addFreshEntity(naiwa)) {
                squad.forEach(NaiwaEntity::discard);
                return new Result("dispatch_failed");
            }
            squad.add(naiwa);
        }
        if (!cost.consume(player)) {
            squad.forEach(NaiwaEntity::discard);
            return new Result("cannot_afford", price);
        }

        book.start(level, player.getUUID(), orderId, squad);
        for (NaiwaEntity naiwa : squad) {
            double x = naiwa.getX();
            double y = naiwa.getY() + 0.8;
            double z = naiwa.getZ();
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 18, 0.65, 0.45, 0.65, 0.06);
            level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 14, 0.55, 0.45, 0.55, 0.08);
            naiwa.startLaughing(NaiwaEntity.DEFAULT_LAUGH_TICKS, false);
        }
        Vec3 soundPos = count == 1 ? squad.getFirst().position() : player.position();
        level.playSound(null, soundPos.x, player.getY() + 2, soundPos.z,
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.9F, 1.15F);
        level.playSound(null, soundPos.x, player.getY() + 2, soundPos.z,
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.25F);
        level.playSound(null, soundPos.x, player.getY() + 2, soundPos.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.35F, 1.6F);
        return new Result(count == 1 ? "summon_one" : "summon_three");
    }

    @Nullable
    private static NaiwaEntity createAirdrop(ServerPlayer player, ServerLevel level, UUID orderId, double x, double z) {
        NaiwaEntity naiwa = ModEntities.NAIWA.get().create(level);
        if (naiwa == null) {
            return null;
        }
        for (double offset = 6.0; offset >= 0.5; offset -= 0.5) {
            double y = player.getY() + offset;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.noCollision(naiwa, naiwa.getType().getDimensions().makeBoundingBox(x, y, z))) {
                continue;
            }
            float yaw = Mth.wrapDegrees(player.getYRot() + 180.0F);
            naiwa.moveTo(x, y, z, yaw, 0.0F);
            naiwa.yBodyRot = yaw;
            naiwa.yHeadRot = yaw;
            naiwa.setOwnerUuid(player.getUUID());
            naiwa.setDidiOrderId(orderId);
            naiwa.setOrderMode(NaiwaEntity.MODE_FOLLOW);
            naiwa.markAirdropSpawn();
            return naiwa;
        }
        return null;
    }

    private record Result(String key, int count) {
        private static final Result SILENT = new Result("", 0);

        private Result(String key) {
            this(key, 0);
        }
    }
}
