package org.gwfx.universaltool.space;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.PersonalDimensionSyncPayload;

import java.util.*;
import java.util.stream.Collectors;

/** Runtime dimension lifecycle used internally by Universal Tool. */
public final class PersonalDimensionRegistry {
    private final MinecraftServer server;
    private final Registry<DimensionType> dimensionTypes;
    private final Registry<LevelStem> levelStems;
    private final Map<UUID, Set<ResourceLocation>> clientReady = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private record PendingTeleport(ResourceLocation dimension, int deadline, Runnable action) {}

    public PersonalDimensionRegistry(MinecraftServer server) {
        this.server = server;
        this.dimensionTypes = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        this.levelStems = server.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
    }

    public static PersonalDimensionRegistry from(MinecraftServer server) {
        return ((PersonalDimensionServerAccess) server).universalTool$dimensionRegistry();
    }

    public void adoptExistingDimension(ServerLevel level) {
        var keys = ((PersonalDimensionServerAccess) server).universalTool$dynamicDimensions();
        if (!keys.contains(level.dimension())) keys.add(level.dimension());
    }

    public boolean canCreateDimension(ResourceLocation id) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return !server.levelKeys().contains(key) && !dimensionTypes.containsKey(id)
                && !levelStems.containsKey(id) && !((PersonalDimensionServerAccess) server)
                .universalTool$dynamicDimensions().contains(key);
    }

    public ServerLevel createDynamicDimension(ResourceLocation id, ChunkGenerator generator, DimensionType type) {
        return createDimension(id, generator, type);
    }

    public ServerLevel loadDynamicDimension(ResourceLocation id, ChunkGenerator generator, DimensionType type) {
        // The caller only loads IDs recorded in its SavedData; never erase existing level data here.
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel loaded = server.getLevel(key);
        if (loaded != null) return loaded;
        if ((dimensionTypes.containsKey(id) || levelStems.containsKey(id))
                && !((PersonalDimensionServerAccess) server).universalTool$dynamicDimensions().contains(key)) {
            UniversalToolMod.LOGGER.error("Cannot restore personal dimension {} because its registry ID is occupied", id);
            return null;
        }
        return registerDimension(id, generator, type, key);
    }

    private ServerLevel createDimension(ResourceLocation id, ChunkGenerator generator, DimensionType type) {
        if (!canCreateDimension(id)) return null;
        if (dimensionTypes.stream().anyMatch(existing -> existing == type)) {
            UniversalToolMod.LOGGER.error("Personal dimension {} was given a DimensionType already in use", id);
            return null;
        }
        return registerDimension(id, generator, type, ResourceKey.create(Registries.DIMENSION, id));
    }

    private ServerLevel registerDimension(ResourceLocation id, ChunkGenerator generator, DimensionType type,
                                          ResourceKey<Level> key) {
        Holder.Reference<DimensionType> typeHolder = dimensionTypes.containsKey(id)
                ? dimensionTypes.getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, id))
                : PersonalRegistryUtil.register(dimensionTypes, id, type);
        LevelStem stem = levelStems.containsKey(id) ? levelStems.get(id) : new LevelStem(typeHolder, generator);
        if (!levelStems.containsKey(id)) PersonalRegistryUtil.register(levelStems, id, stem);
        ServerLevel level = createLevel(key, server.getWorldData(), stem, server.overworld());
        CompoundTag serializedType = (CompoundTag) DimensionType.DIRECT_CODEC.encode(typeHolder.value(),
                RegistryOps.create(NbtOps.INSTANCE, server.registryAccess()), new CompoundTag()).getOrThrow();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new PersonalDimensionSyncPayload(id, serializedType, false));
        }
        reloadDimensionTags();
        return level;
    }

    private ServerLevel createLevel(ResourceKey<Level> key, WorldData worldData, LevelStem stem, ServerLevel overworld) {
        var accessor = (PersonalMinecraftServerAccess) server;
        var levelData = new PersonalDimensionLevelData(worldData, worldData.overworldData());
        var level = new ServerLevel(server, accessor.universalTool$getExecutor(), accessor.universalTool$getStorageSource(),
                levelData, key, stem,
                accessor.universalTool$getProgressListenerFactory().create(10), worldData.isDebugWorld(),
                BiomeManager.obfuscateSeed(worldData.worldGenOptions().seed()), ImmutableList.of(), false, null);
        levelData.attach(level);
        level.getChunkSource().setSimulationDistance(((PersonalDistanceManagerAccess)
                ((PersonalServerChunkCacheAccess) overworld.getChunkSource()).universalTool$getDistanceManager())
                .universalTool$getSimulationDistance());
        level.getChunkSource().setViewDistance(((PersonalChunkMapAccess) overworld.getChunkSource().chunkMap)
                .universalTool$getViewDistance());

        ForcedChunksSavedData forcedChunks = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        if (forcedChunks != null) {
            LongIterator iterator = forcedChunks.getChunks().iterator();
            while (iterator.hasNext()) level.getChunkSource().updateChunkForced(new ChunkPos(iterator.nextLong()), true);
        }
        level.setSpawnSettings(server.isSpawningMonsters(), server.isSpawningAnimals());
        ((PersonalDimensionServerAccess) server).universalTool$registerLevel(level);
        return level;
    }

    public boolean unloadDynamicDimension(ResourceLocation id) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        if (!((PersonalDimensionServerAccess) server).universalTool$dynamicDimensions().contains(key)) return false;
        ((PersonalDimensionServerAccess) server).universalTool$unloadLevel(key);
        return true;
    }

    public void whenClientReady(ServerPlayer player, ServerLevel destination, Runnable action) {
        if (server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.serverLevel() == destination
                || clientReady.getOrDefault(player.getUUID(), Set.of()).contains(destination.dimension().location())) {
            pendingTeleports.remove(player.getUUID());
            action.run();
            return;
        }
        ResourceLocation id = destination.dimension().location();
        pendingTeleports.put(player.getUUID(), new PendingTeleport(id, server.getTickCount() + 200, action));
        CompoundTag type = (CompoundTag) DimensionType.DIRECT_CODEC.encode(destination.dimensionType(),
                RegistryOps.create(NbtOps.INSTANCE, server.registryAccess()), new CompoundTag()).getOrThrow();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new PersonalDimensionSyncPayload(id, type, false));
    }

    public void acknowledge(ServerPlayer player, ResourceLocation dimension) {
        if (!dimensionTypes.containsKey(dimension)) return;
        clientReady.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(dimension);
    }

    public void tickPendingTeleports() {
        for (var entry : List.copyOf(pendingTeleports.entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            var pending = entry.getValue();
            if (player == null) { pendingTeleports.remove(entry.getKey()); continue; }
            if (clientReady.getOrDefault(entry.getKey(), Set.of()).contains(pending.dimension())) {
                pendingTeleports.remove(entry.getKey());pending.action().run();
            } else if (server.getTickCount() > pending.deadline()) {
                pendingTeleports.remove(entry.getKey());
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.universal_tool.personal_space.load_failed"), true);
                var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, pending.dimension()));
                if (level != null && level.players().isEmpty()) unloadDynamicDimension(pending.dimension());
            }
        }
    }

    public void forgetClient(UUID player) { clientReady.remove(player);pendingTeleports.remove(player); }

    public boolean hasPendingTeleport(ResourceLocation dimension) {
        return pendingTeleports.values().stream().anyMatch(pending -> pending.dimension().equals(dimension));
    }

    public void invalidateClientDimension(ResourceLocation dimension) {
        clientReady.values().forEach(ready -> ready.remove(dimension));
        pendingTeleports.entrySet().removeIf(entry -> entry.getValue().dimension().equals(dimension));
    }

    private void reloadDimensionTags() {
        var resources = ((PersonalMinecraftServerAccess) server).universalTool$getResources();
        for (TagManager.LoadResult<?> result : ((PersonalReloadableResourcesAccess) resources.managers())
                .universalTool$getTagManager().getResult()) {
            if (result.key() == Registries.DIMENSION_TYPE) {
                dimensionTypes.resetTags();
                @SuppressWarnings("unchecked")
                TagManager.LoadResult<DimensionType> typed = (TagManager.LoadResult<DimensionType>) result;
                dimensionTypes.bindTags(typed.tags().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                        entry -> TagKey.create(Registries.DIMENSION_TYPE, entry.getKey()),
                        entry -> entry.getValue().stream().toList())));
                break;
            }
        }
        server.getPlayerList().broadcastAll(new ClientboundUpdateTagsPacket(
                TagNetworkSerialization.serializeTagsToNetwork(server.registries())));
    }
}
