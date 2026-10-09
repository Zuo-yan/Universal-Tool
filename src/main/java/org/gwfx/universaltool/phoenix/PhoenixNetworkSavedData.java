package org.gwfx.universaltool.phoenix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class PhoenixNetworkSavedData extends SavedData {

    private static final String DATA_NAME = "universal_tool_phoenix_network";

    public record PodEntry(ResourceKey<Level> dimension, BlockPos pos, UUID ownerUUID) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension.location().toString());
            tag.putLong("Pos", pos.asLong());
            tag.putUUID("OwnerUUID", ownerUUID);
            return tag;
        }

        public static PodEntry fromTag(CompoundTag tag) {
            ResourceLocation dimId = ResourceLocation.parse(tag.getString("Dimension"));
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
            BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            UUID uuid = tag.getUUID("OwnerUUID");
            return new PodEntry(dim, pos, uuid);
        }
    }

    private final List<PodEntry> readyPods = new ArrayList<>();

    public static PhoenixNetworkSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            overworld = server.overworld();
        }
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(PhoenixNetworkSavedData::new, PhoenixNetworkSavedData::load),
                DATA_NAME
        );
    }

    public void registerPod(ResourceKey<Level> dimension, BlockPos pos, UUID ownerUUID) {
        unregisterPod(dimension, pos);
        readyPods.add(new PodEntry(dimension, pos, ownerUUID));
        setDirty();
    }

    public void unregisterPod(ResourceKey<Level> dimension, BlockPos pos) {
        readyPods.removeIf(entry -> entry.dimension.equals(dimension) && entry.pos.equals(pos));
        setDirty();
    }

    public Optional<PodEntry> findBestPod(MinecraftServer server, Player player) {
        UUID uuid = player.getUUID();
        ResourceKey<Level> currentDim = player.level().dimension();
        BlockPos playerPos = player.blockPosition();

        PodEntry bestSameDim = null;
        double bestDistSq = Double.MAX_VALUE;
        PodEntry backupOtherDim = null;

        List<PodEntry> invalidEntries = new ArrayList<>();

        for (PodEntry entry : readyPods) {
            if (!entry.ownerUUID.equals(uuid)) {
                continue;
            }

            ServerLevel level = server.getLevel(entry.dimension);
            if (level == null) {
                continue;
            }

            // 验证方块实体是否有效且真的处于 READY
            if (level.getBlockEntity(entry.pos) instanceof PhoenixPodBlockEntity be && be.isReady()) {
                if (entry.dimension.equals(currentDim)) {
                    double distSq = entry.pos.distSqr(playerPos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestSameDim = entry;
                    }
                } else if (backupOtherDim == null) {
                    backupOtherDim = entry;
                }
            } else {
                invalidEntries.add(entry);
            }
        }

        // 清理失效记录
        if (!invalidEntries.isEmpty()) {
            readyPods.removeAll(invalidEntries);
            setDirty();
        }

        if (bestSameDim != null) {
            return Optional.of(bestSameDim);
        }
        return Optional.ofNullable(backupOtherDim);
    }

    public static PhoenixNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PhoenixNetworkSavedData data = new PhoenixNetworkSavedData();
        ListTag list = tag.getList("Pods", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.readyPods.add(PodEntry.fromTag(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PodEntry entry : readyPods) {
            list.add(entry.toTag());
        }
        tag.put("Pods", list);
        return tag;
    }
}
