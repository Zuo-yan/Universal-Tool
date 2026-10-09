package org.gwfx.universaltool.phoenix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.PhoenixCinematicStartPayload;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public class PhoenixRebirthHandler {

    private static class RebirthTask {
        final UUID playerUUID;
        final ResourceKey<Level> targetDim;
        final BlockPos podPos;
        final Direction podFacing;
        int remainingTicks = 240; // 翻倍为 12 秒 (240 ticks)

        RebirthTask(UUID playerUUID, ResourceKey<Level> targetDim, BlockPos podPos, Direction podFacing) {
            this.playerUUID = playerUUID;
            this.targetDim = targetDim;
            this.podPos = podPos;
            this.podFacing = podFacing;
        }
    }

    private static final List<RebirthTask> ACTIVE_TASKS = new ArrayList<>();

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        PhoenixNetworkSavedData networkData = PhoenixNetworkSavedData.get(server);
        Optional<PhoenixNetworkSavedData.PodEntry> bestPodOpt = networkData.findBestPod(server, player);

        if (bestPodOpt.isEmpty()) {
            return;
        }

        PhoenixNetworkSavedData.PodEntry targetPod = bestPodOpt.get();
        ServerLevel destLevel = server.getLevel(targetPod.dimension());
        if (destLevel == null) {
            return;
        }

        BlockPos podPos = targetPod.pos();
        if (!(destLevel.getBlockEntity(podPos) instanceof PhoenixPodBlockEntity podBe) || !podBe.isReady()) {
            networkData.unregisterPod(targetPod.dimension(), podPos);
            return;
        }

        // 1. 成功拦截致命伤害！
        event.setCanceled(true);

        // 2. 全程绝对无敌防御与状态净化
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.clearFire();
        player.resetFallDistance();
        player.setInvulnerable(true);

        BlockState podBlockState = destLevel.getBlockState(podPos);
        Direction facing = podBlockState.hasProperty(PhoenixPodBlock.FACING) ? podBlockState.getValue(PhoenixPodBlock.FACING) : Direction.NORTH;

        // 3. 向客户端同步 12 秒 (240 ticks) 电影级长镜头指令
        PacketDistributor.sendToPlayer(player, new PhoenixCinematicStartPayload(podPos, facing.get2DDataValue(), targetPod.dimension().location().toString(), 240));

        // 4. 注册服务端协同追踪任务
        ACTIVE_TASKS.add(new RebirthTask(player.getUUID(), targetPod.dimension(), podPos, facing));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (ACTIVE_TASKS.isEmpty()) return;

        Iterator<RebirthTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            RebirthTask task = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(task.playerUUID);
            if (player == null) {
                it.remove();
                continue;
            }

            task.remainingTicks--;

            ServerLevel destLevel = server.getLevel(task.targetDim);
            if (destLevel == null) {
                it.remove();
                player.setInvulnerable(false);
                continue;
            }

            // 阶段 2 (剩余 180 ticks / 过了 60 ticks)：将玩家无感传送到目标培养仓内部
            if (task.remainingTicks == 180) {
                double insideX = task.podPos.getX() + 0.5;
                double insideY = task.podPos.getY() + 0.3;
                double insideZ = task.podPos.getZ() + 0.5;
                float yaw = task.podFacing.toYRot();

                if (player.level() != destLevel) {
                    player.teleportTo(destLevel, insideX, insideY, insideZ, yaw, 0.0F);
                } else {
                    player.teleportTo(insideX, insideY, insideZ);
                    player.setYRot(yaw);
                }
            }

            // 阶段 5 开启 (剩余 50 ticks / 过了 190 ticks)：启动液压向上大门升起序列 (开门 40 ticks, 保持 60 ticks, 关门 40 ticks)
            if (task.remainingTicks == 50) {
                if (destLevel.getBlockEntity(task.podPos) instanceof PhoenixPodBlockEntity podBe) {
                    podBe.triggerDoorSequence();
                }
            }

            // 动画完成 (剩余 0 ticks / 240 ticks 结束)：迈步出舱，战备重塑，交还控制！
            if (task.remainingTicks <= 0) {
                it.remove();

                BlockPos exitPos = task.podPos.relative(task.podFacing);
                double exitX = exitPos.getX() + 0.5;
                double exitY = exitPos.getY();
                double exitZ = exitPos.getZ() + 0.5;
                float exitYaw = task.podFacing.toYRot();

                player.teleportTo(exitX, exitY, exitZ);
                player.setYRot(exitYaw);
                player.setXRot(0.0F);

                // 解除无敌并给予出舱保护
                player.setInvulnerable(false);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 3, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, false, false, false));

                // 检查并恢复战备快照 (绝对克隆)
                if (destLevel.getBlockEntity(task.podPos) instanceof PhoenixPodBlockEntity podBe) {
                    ListTag snapshotList = podBe.getSnapshotTag();
                    if (snapshotList != null && !snapshotList.isEmpty()) {
                        player.getInventory().clearContent();

                        for (int i = 0; i < snapshotList.size(); i++) {
                            CompoundTag entry = snapshotList.getCompound(i);
                            int slot = entry.getInt("Slot");
                            ItemStack restoredStack = ItemStack.parse(destLevel.registryAccess(), entry.getCompound("Item")).orElse(ItemStack.EMPTY);

                            if (!restoredStack.isEmpty()) {
                                if (slot < 36) {
                                    player.getInventory().items.set(slot, restoredStack);
                                } else if (slot >= 36 && slot < 40) {
                                    player.getInventory().armor.set(slot - 36, restoredStack);
                                } else if (slot == 40) {
                                    player.getInventory().offhand.set(0, restoredStack);
                                }
                            }
                        }
                        player.sendSystemMessage(Component.translatable("message.universal_tool.snapshot_restored"));
                    }

                    // 消耗克隆体
                    podBe.consumeClone();
                }

                // 产生水花喷溅与排气粒子
                destLevel.sendParticles(ParticleTypes.SPLASH, exitX, exitY + 0.5, exitZ, 40, 0.4, 0.5, 0.4, 0.2);
                destLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, exitX, exitY + 0.8, exitZ, 25, 0.3, 0.5, 0.3, 0.05);

                player.sendSystemMessage(Component.translatable("message.universal_tool.phoenix.rebirth_success"));
            }
        }
    }
}
