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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.PhoenixCinematicStartPayload;

import javax.annotation.Nullable;
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
        @Nullable
        final ListTag snapshotTag;
        int remainingTicks = 240; // 12 seconds (240 ticks)

        RebirthTask(UUID playerUUID, ResourceKey<Level> targetDim, BlockPos podPos, Direction podFacing,
                    @Nullable ListTag snapshotTag) {
            this.playerUUID = playerUUID;
            this.targetDim = targetDim;
            this.podPos = podPos;
            this.podFacing = podFacing;
            this.snapshotTag = snapshotTag;
        }
    }

    private static final List<RebirthTask> ACTIVE_TASKS = new ArrayList<>();

    private static boolean isRebirthing(UUID playerUUID) {
        for (RebirthTask task : ACTIVE_TASKS) {
            if (task.playerUUID.equals(playerUUID)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Prevent duplicate rebirth tasks if lethal damage fires again during the cinematic.
        if (isRebirthing(player.getUUID())) {
            event.setCanceled(true);
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

        // 1. Intercept lethal damage and reserve the pod immediately so no second death can claim it.
        event.setCanceled(true);
        networkData.unregisterPod(targetPod.dimension(), podPos);

        ListTag rawSnapshot = podBe.getSnapshotTag();
        ListTag snapshotCopy = (rawSnapshot != null && !rawSnapshot.isEmpty()) ? rawSnapshot.copy() : null;

        // 2. Close open containers & clear cursor stack; if a snapshot will replace the inventory,
        // clear the old inventory immediately so items cannot be dropped or stashed during the 12s cinematic.
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.closeContainer();
        if (snapshotCopy != null) {
            player.getInventory().clearContent();
            player.inventoryMenu.broadcastChanges();
        }

        // 3. Invulnerability and status purification
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.clearFire();
        player.resetFallDistance();
        player.setInvulnerable(true);

        BlockState podBlockState = destLevel.getBlockState(podPos);
        Direction facing = podBlockState.hasProperty(PhoenixPodBlock.FACING) ? podBlockState.getValue(PhoenixPodBlock.FACING) : Direction.NORTH;

        // 4. Sync 12s cinematic to client
        PacketDistributor.sendToPlayer(player, new PhoenixCinematicStartPayload(podPos, facing.get2DDataValue(), targetPod.dimension().location().toString(), 240));

        // 5. Register server tracking task
        ACTIVE_TASKS.add(new RebirthTask(player.getUUID(), targetPod.dimension(), podPos, facing, snapshotCopy));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isRebirthing(player.getUUID())) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isRebirthing(player.getUUID())) {
            ItemStack tossed = event.getEntity().getItem();
            if (!tossed.isEmpty()) {
                player.getInventory().add(tossed);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Iterator<RebirthTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            RebirthTask task = it.next();
            if (task.playerUUID.equals(player.getUUID())) {
                it.remove();
                finalizeRebirth(server, task, player, false);
            }
        }
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

            // Stage 2 (remaining 180 ticks): move player inside the pod
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

            // Stage 5 (remaining 50 ticks): trigger hydraulic door animation
            if (task.remainingTicks == 50) {
                if (destLevel.getBlockEntity(task.podPos) instanceof PhoenixPodBlockEntity podBe) {
                    podBe.triggerDoorSequence();
                }
            }

            // Rebirth complete
            if (task.remainingTicks <= 0) {
                it.remove();
                finalizeRebirth(server, task, player, true);
            }
        }
    }

    private static void finalizeRebirth(MinecraftServer server, RebirthTask task, ServerPlayer player, boolean sendEffects) {
        ServerLevel destLevel = server.getLevel(task.targetDim);
        player.setInvulnerable(false);

        if (destLevel == null) {
            return;
        }

        BlockPos exitPos = task.podPos.relative(task.podFacing);
        double exitX = exitPos.getX() + 0.5;
        double exitY = exitPos.getY();
        double exitZ = exitPos.getZ() + 0.5;
        float exitYaw = task.podFacing.toYRot();

        if (player.level() != destLevel) {
            player.teleportTo(destLevel, exitX, exitY, exitZ, exitYaw, 0.0F);
        } else {
            player.teleportTo(exitX, exitY, exitZ);
            player.setYRot(exitYaw);
            player.setXRot(0.0F);
        }

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 3, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, false, false, false));

        if (task.snapshotTag != null && !task.snapshotTag.isEmpty()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.closeContainer();
            player.getInventory().clearContent();

            for (int i = 0; i < task.snapshotTag.size(); i++) {
                CompoundTag entry = task.snapshotTag.getCompound(i);
                int slot = entry.getInt("Slot");
                ItemStack restoredStack = ItemStack.parse(destLevel.registryAccess(), entry.getCompound("Item")).orElse(ItemStack.EMPTY);

                if (!restoredStack.isEmpty()) {
                    if (slot >= 0 && slot < 36) {
                        player.getInventory().items.set(slot, restoredStack);
                    } else if (slot >= 36 && slot < 40) {
                        player.getInventory().armor.set(slot - 36, restoredStack);
                    } else if (slot == 40) {
                        player.getInventory().offhand.set(0, restoredStack);
                    }
                }
            }
            player.inventoryMenu.broadcastChanges();
            if (sendEffects) {
                player.sendSystemMessage(Component.translatable("message.universal_tool.snapshot_restored"));
            }
        }

        if (destLevel.getBlockEntity(task.podPos) instanceof PhoenixPodBlockEntity podBe) {
            podBe.consumeClone();
        }

        if (sendEffects) {
            destLevel.sendParticles(ParticleTypes.SPLASH, exitX, exitY + 0.5, exitZ, 40, 0.4, 0.5, 0.4, 0.2);
            destLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, exitX, exitY + 0.8, exitZ, 25, 0.3, 0.5, 0.3, 0.05);
            player.sendSystemMessage(Component.translatable("message.universal_tool.phoenix.rebirth_success"));
        }
    }
}
