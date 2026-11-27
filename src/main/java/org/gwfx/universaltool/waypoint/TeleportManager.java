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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModParticles;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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

        TeleportTask(ServerPlayer player, Waypoint target) {
            this.player = player;
            this.target = target;
            this.startPos = player.position();
            this.ticksRemaining = org.gwfx.universaltool.skill.SkillModSynergyHandler
                    .adjustTeleportChargeTicks(player, CHARGE_TICKS);
        }
    }

    private static final Map<UUID, TeleportTask> ACTIVE_TASKS = new HashMap<>();

    public static boolean startTeleport(ServerPlayer player, Waypoint target, boolean fromScroll) {
        if (!fromScroll && player.getCooldowns().isOnCooldown(ModItems.TELEPORT_CODEX.get())) {
            player.displayClientMessage(Component.translatable("message.universal_tool.on_cooldown"), true);
            return false;
        }

        ServerLevel destLevel = destination(player, target);
        if (destLevel == null) return false;
        Vec3 destSafePos = landing(player, destLevel, target);
        if (destSafePos == null) return false;
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
            return executeTeleport(player, target);
        }
        wormhole.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        wormhole.configure(player, target, player.getYRot() + 180.0F, fromScroll);
        byte[] liveVista = LiveVistaSampler.captureCompressed(player.server, target);
        if (liveVista != null) {
            String vistaId = "live_" + player.getUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis();
            wormhole.setVistaId(vistaId);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    new org.gwfx.universaltool.network.WormholeLiveVistaPayload(
                            vistaId, LiveVistaSampler.WIDTH, LiveVistaSampler.HEIGHT, liveVista)
            );
        }
        if (!level.addFreshEntity(wormhole)) return false;

        WormholeEntity destExit = spawnDestinationExitWormhole(
                destLevel, player.getUUID(), destSafePos, target.yaw(), -1);
        if (destExit != null) {
            wormhole.linkDestinationExit(destLevel.dimension(), destExit.getUUID(), destExit.blockPosition());
        }

        level.playSound(null, spawnPos.x, spawnPos.y + 1.0, spawnPos.z,
                SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.55F, 1.45F);
        level.playSound(null, spawnPos.x, spawnPos.y + 1.0, spawnPos.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.65F, 1.25F);
        level.sendParticles(ModParticles.WORMHOLE_SPARK.get(),
                spawnPos.x, spawnPos.y + 1.1, spawnPos.z, 26, 0.35, 0.65, 0.35, 0.08);

        player.displayClientMessage(Component.translatable("message.universal_tool.wormhole_opened", target.name()), true);
        return true;
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
                executeTeleport(player, task.target);
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

    public static boolean executeTeleport(ServerPlayer player, Waypoint target) {
        ServerLevel destLevel = destination(player, target);
        if (destLevel == null) return false;
        Vec3 safePos = landing(player, destLevel, target);
        if (safePos == null) return false;

        ServerLevel origin = player.serverLevel();
        Vec3 from = player.position();
        if (!movePlayer(player, destLevel, safePos, target.yaw(), target.pitch())) return false;
        origin.playSound(null, from.x, from.y, from.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 目的地点音效与自定义粒子 + 维持/收拢出口虫洞
        destLevel.playSound(null, safePos.x, safePos.y, safePos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        destLevel.sendParticles(
                ModParticles.WORMHOLE_EXIT_SPARK.get(),
                safePos.x, safePos.y + 1.0, safePos.z,
                28, 0.5, 1.0, 0.5, 0.1
        );
        completeOrSpawnDestinationExit(destLevel, player, safePos, target.yaw());

        // 施加冷却
        int cooldownTicks = org.gwfx.universaltool.skill.SkillModSynergyHandler
                .adjustTeleportCooldownTicks(player, org.gwfx.universaltool.config.ModConfigs.getTeleportCooldownTicks());
        if (cooldownTicks > 0) {
            player.getCooldowns().addCooldown(ModItems.TELEPORT_CODEX.get(), cooldownTicks);
        }
        org.gwfx.universaltool.skill.SkillModSynergyHandler.onTeleportCompleted(player);
        player.displayClientMessage(Component.translatable("message.universal_tool.teleport_success", target.name()), true);
        return true;
    }

    private static ServerLevel destination(ServerPlayer player, Waypoint target) {
        var dimension = ResourceKey.create(Registries.DIMENSION, target.dimension());
        ServerLevel destination = player.server.getLevel(dimension);
        if (destination == null) {
            player.displayClientMessage(Component.translatable("message.universal_tool.dimension_not_found"), true);
        }
        return destination;
    }

    private static Vec3 landing(ServerPlayer player, ServerLevel level, Waypoint target) {
        Vec3 landing = WaypointLanding.find(level, new Vec3(target.x(), target.y(), target.z()));
        if (landing == null) {
            player.displayClientMessage(Component.translatable("message.universal_tool.no_safe_landing"), true);
        }
        return landing;
    }

    static boolean returnToOrigin(ServerPlayer player, ResourceKey<net.minecraft.world.level.Level> origin,
                                  Vec3 position, float yaw, float pitch) {
        ServerLevel level = player.server.getLevel(origin);
        if (level != null) {
            Vec3 landing = WaypointLanding.find(level, position);
            if (landing != null && movePlayer(player, level, landing, yaw, pitch)) return true;
        }
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, overworld.getSharedSpawnPos());
        Vec3 landing = WaypointLanding.find(overworld, Vec3.atBottomCenterOf(spawn));
        return landing != null && movePlayer(player, overworld, landing, yaw, pitch);
    }

    private static boolean movePlayer(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        if (!player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch)
                || player.serverLevel() != level) return false;
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        return true;
    }

    private static void completeOrSpawnDestinationExit(ServerLevel destLevel, ServerPlayer player,
                                                         Vec3 safePos, float arrivalYaw) {
        WormholeEntity activeExit = null;
        for (WormholeEntity existing : destLevel.getEntitiesOfClass(
                WormholeEntity.class, new net.minecraft.world.phys.AABB(safePos, safePos).inflate(16.0D))) {
            if (existing.isDestinationExit() && !existing.isClosing()
                    && player.getUUID().equals(existing.getOwnerUuid())) {
                if (activeExit == null && existing.position().distanceToSqr(safePos) <= 9
                        && WaypointLanding.isSafe(destLevel, existing.position())) {
                    activeExit = existing;
                } else {
                    existing.beginClosing();
                }
            }
        }
        if (activeExit != null) {
            activeExit.scheduleCloseAfter(100);
        } else {
            spawnDestinationExitWormhole(destLevel, player.getUUID(), safePos, arrivalYaw, 120);
        }
    }

    private static WormholeEntity spawnDestinationExitWormhole(ServerLevel destLevel, UUID ownerUuid,
                                                               Vec3 safePos, float arrivalYaw, int closeAfterTicks) {
        for (WormholeEntity existing : destLevel.getEntitiesOfClass(
                WormholeEntity.class, new net.minecraft.world.phys.AABB(safePos, safePos).inflate(16.0D))) {
            if (existing.isDestinationExit() && ownerUuid.equals(existing.getOwnerUuid())) {
                existing.beginClosing();
            }
        }
        Vec3 forwardHoriz = Vec3.directionFromRotation(0.0F, arrivalYaw).normalize();
        Vec3 exitPos = findWormholeSpawnPos(destLevel, safePos, forwardHoriz.scale(-1.0D));
        if (!WaypointLanding.isSafe(destLevel, exitPos)) exitPos = safePos;
        WormholeEntity exitWormhole = org.gwfx.universaltool.init.ModEntities.WORMHOLE.get().create(destLevel);
        if (exitWormhole != null) {
            exitWormhole.setPos(exitPos.x, exitPos.y, exitPos.z);
            exitWormhole.configureDestinationExit(ownerUuid, arrivalYaw);
            if (closeAfterTicks > 0) {
                exitWormhole.scheduleCloseAfter(closeAfterTicks);
            }
            destLevel.addFreshEntity(exitWormhole);
        }
        return exitWormhole;
    }

    /**
     * Teleports a dropped item entity directly through a wormhole to the waypoint destination.
     */
    public static boolean teleportItem(ItemEntity itemEntity, Waypoint target, UUID owner) {
        if (!itemEntity.isAlive() || itemEntity.getItem().isEmpty()) {
            return false;
        }
        if (!(itemEntity.level() instanceof ServerLevel sourceLevel)) {
            return false;
        }
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, target.dimension());
        ServerLevel destLevel = sourceLevel.getServer().getLevel(dimKey);
        if (destLevel == null) {
            return false;
        }

        Vec3 safePos = WaypointLanding.find(destLevel, new Vec3(target.x(), target.y(), target.z()));
        if (safePos == null) return false;

        double spawnX = safePos.x;
        double spawnY = safePos.y + 0.35D;
        double spawnZ = safePos.z;
        Vec3 velocity = new Vec3(0.0D, 0.12D, 0.0D);

        for (WormholeEntity existing : destLevel.getEntitiesOfClass(
                WormholeEntity.class, new net.minecraft.world.phys.AABB(safePos, safePos).inflate(3.0D))) {
            if (existing.isDestinationExit() && !existing.isClosing() && owner != null && owner.equals(existing.getOwnerUuid())
                    && WaypointLanding.isSafe(destLevel, existing.position())) {
                spawnX = existing.getX();
                spawnY = existing.getY() + 1.05D;
                spawnZ = existing.getZ();
                Vec3 toLanding = new Vec3(safePos.x - spawnX, 0.0D, safePos.z - spawnZ);
                if (toLanding.lengthSqr() > 0.01D) {
                    Vec3 dir = toLanding.normalize();
                    velocity = new Vec3(dir.x * 0.18D, 0.12D, dir.z * 0.18D);
                }
                break;
            }
        }

        ItemEntity arrived = new ItemEntity(
                destLevel,
                spawnX,
                spawnY,
                spawnZ,
                itemEntity.getItem().copy()
        );
        arrived.setPickUpDelay(15);
        arrived.setDeltaMovement(velocity);
        if (!destLevel.addFreshEntity(arrived)) return false;
        itemEntity.discard();

        destLevel.playSound(null, spawnX, spawnY, spawnZ,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.45F, 1.6F);
        destLevel.sendParticles(
                ModParticles.WORMHOLE_EXIT_SPARK.get(),
                spawnX, spawnY + 0.2D, spawnZ,
                16, 0.25D, 0.35D, 0.25D, 0.05D
        );
        return true;
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