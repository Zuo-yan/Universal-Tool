package org.gwfx.universaltool.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public class TeleportManager {

    public static final int CHARGE_TICKS = 30; // 1.5 秒
    public static final int COOLDOWN_TICKS = 100; // 5 秒冷却

    private static class TeleportTask {
        ServerPlayer player;
        Waypoint target;
        Vec3 startPos;
        int ticksRemaining;
        boolean fromScroll;

        TeleportTask(ServerPlayer player, Waypoint target, boolean fromScroll) {
            this.player = player;
            this.target = target;
            this.startPos = player.position();
            this.ticksRemaining = CHARGE_TICKS;
            this.fromScroll = fromScroll;
        }
    }

    private static final Map<UUID, TeleportTask> ACTIVE_TASKS = new HashMap<>();

    public static void startTeleport(ServerPlayer player, Waypoint target, boolean fromScroll) {
        if (!fromScroll && player.getCooldowns().isOnCooldown(ModItems.TELEPORT_CODEX.get())) {
            player.displayClientMessage(Component.translatable("message.universal_tool.on_cooldown"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        // 清理该玩家之前尚未进入的旧虫洞
        for (WormholeEntity existing : level.getEntitiesOfClass(WormholeEntity.class, player.getBoundingBox().inflate(32.0D))) {
            if (player.getUUID().equals(existing.getOwnerUuid())) {
                existing.beginClosing();
            }
        }

        Vec3 lookHoriz = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
        Vec3 spawnPos = findWormholeSpawnPos(level, player.position(), lookHoriz);

        WormholeEntity wormhole = org.gwfx.universaltool.init.ModEntities.WORMHOLE.get().create(level);
        if (wormhole == null) {
            executeTeleport(player, target, fromScroll);
            return;
        }
        wormhole.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        wormhole.configure(player, target, player.getYRot() + 180.0F, fromScroll);
        level.addFreshEntity(wormhole);

        level.playSound(null, spawnPos.x, spawnPos.y + 1.0, spawnPos.z,
                SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.55F, 1.45F);
        level.playSound(null, spawnPos.x, spawnPos.y + 1.0, spawnPos.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.65F, 1.25F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                spawnPos.x, spawnPos.y + 1.1, spawnPos.z, 26, 0.35, 0.65, 0.35, 0.08);

        player.displayClientMessage(Component.translatable("message.universal_tool.wormhole_opened", target.name()), true);
    }

    private static Vec3 findWormholeSpawnPos(ServerLevel level, Vec3 origin, Vec3 dir) {
        for (double dist = 2.3D; dist >= 1.2D; dist -= 0.35D) {
            Vec3 candidate = origin.add(dir.scale(dist));
            BlockPos feet = BlockPos.containing(candidate.x, candidate.y + 0.1, candidate.z);
            BlockPos head = feet.above();
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                return candidate;
            }
        }
        return origin.add(dir.scale(1.4D));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_TASKS.isEmpty()) return;

        Iterator<Map.Entry<UUID, TeleportTask>> it = ACTIVE_TASKS.entrySet().iterator();
        while (it.hasNext()) {
            TeleportTask task = it.next().getValue();
            ServerPlayer player = task.player;

            // 1. 检查玩家是否存活
            if (player.isRemoved() || !player.isAlive()) {
                it.remove();
                continue;
            }

            // 2. 检查移动打断 (位移超过 0.5 格)
            if (player.position().distanceToSqr(task.startPos) > 0.25) {
                player.displayClientMessage(Component.translatable("message.universal_tool.interrupted_moved"), true);
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 0.8F);
                it.remove();
                continue;
            }

            // 3. 产生脚底回溯末影符文粒子
            player.serverLevel().sendParticles(
                    net.minecraft.core.particles.ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 0.2, player.getZ(),
                    6, 0.2, 0.1, 0.2, 0.05
            );

            // 4. 倒计时推进
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                // 蓄力完毕，执行跃迁
                it.remove();
                executeTeleport(player, task.target, task.fromScroll);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportTask task = ACTIVE_TASKS.remove(player.getUUID());
            if (task != null) {
                player.displayClientMessage(Component.translatable("message.universal_tool.interrupted_damage"), true);
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 0.8F);
            }
        }
    }

    public static void executeTeleport(ServerPlayer player, Waypoint target, boolean fromScroll) {
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, target.dimension());
        ServerLevel destLevel = player.server.getLevel(dimKey);
        if (destLevel == null) {
            player.displayClientMessage(Component.translatable("message.universal_tool.dimension_not_found"), true);
            return;
        }

        // 安全落脚点检测
        Vec3 safePos = findSafePosition(destLevel, target.x(), target.y(), target.z());

        // 原地音效
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 传送
        player.teleportTo(destLevel, safePos.x, safePos.y, safePos.z, target.yaw(), target.pitch());

        // 目的地点音效与粒子
        destLevel.playSound(null, safePos.x, safePos.y, safePos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        destLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                safePos.x, safePos.y + 1.0, safePos.z,
                30, 0.5, 1.0, 0.5, 0.1
        );

        // 施加冷却
        int cooldownTicks = org.gwfx.universaltool.config.ModConfigs.getTeleportCooldownTicks();
        if (cooldownTicks > 0) {
            player.getCooldowns().addCooldown(ModItems.TELEPORT_CODEX.get(), cooldownTicks);
        }
        player.displayClientMessage(Component.translatable("message.universal_tool.teleport_success", target.name()), true);
    }

    private static Vec3 findSafePosition(ServerLevel level, double tx, double ty, double tz) {
        int x = (int) Math.floor(tx);
        int y = (int) Math.floor(ty);
        int z = (int) Math.floor(tz);

        // 检查原目标点脚下是否有坚固方块，且头脚部位无窒息方块
        if (isSafeStand(level, x, y, z)) {
            return new Vec3(tx, ty, tz);
        }

        // 周围 3 格范围搜索安全方块
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (isSafeStand(level, x + dx, y + dy, z + dz)) {
                        return new Vec3(x + dx + 0.5, y + dy, z + dz + 0.5);
                    }
                    if (dy > 0 && isSafeStand(level, x + dx, y - dy, z + dz)) {
                        return new Vec3(x + dx + 0.5, y - dy, z + dz + 0.5);
                    }
                }
            }
        }

        // 保底：原坐标加上 0.1
        return new Vec3(tx, ty + 0.1, tz);
    }

    private static boolean isSafeStand(ServerLevel level, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = feet.above();
        BlockPos below = feet.below();

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState belowState = level.getBlockState(below);

        // 脚底必须有碰撞箱支持，且不是岩浆
        if (belowState.isAir() || belowState.is(Blocks.LAVA) || !belowState.isSolid()) {
            return false;
        }

        // 头脚位置不能是实心窒息方块，也不能是岩浆
        if (feetState.is(Blocks.LAVA) || headState.is(Blocks.LAVA)) {
            return false;
        }

        return !feetState.isSolid() && !headState.isSolid();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WaypointData data = player.getData(WaypointData.ATTACHMENT.get());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new org.gwfx.universaltool.network.SyncWaypointsPayload(data.getWaypoints(), data.getMaxCapacity()));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WaypointData data = player.getData(WaypointData.ATTACHMENT.get());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new org.gwfx.universaltool.network.SyncWaypointsPayload(data.getWaypoints(), data.getMaxCapacity()));
        }
    }

}