package org.gwfx.universaltool.mixin;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.space.PersonalDimensionRegistry;
import org.gwfx.universaltool.space.PersonalDimensionServerAccess;
import org.gwfx.universaltool.network.PersonalDimensionSyncPayload;
import org.gwfx.universaltool.space.PersonalLevelDataAccess;
import org.gwfx.universaltool.space.PersonalMinecraftServerAccess;
import org.gwfx.universaltool.space.PersonalRegistryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public abstract class PersonalMinecraftServerMixin implements PersonalDimensionServerAccess, PersonalMinecraftServerAccess {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;
    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;
    @Shadow public abstract PlayerList getPlayerList();
    @Shadow public abstract LayeredRegistryAccess<RegistryLayer> registries();

    @Unique private final List<ServerLevel> universalTool$pendingLevels = new ArrayList<>();
    @Unique private final List<ResourceKey<Level>> universalTool$pendingUnloads = new ArrayList<>();
    @Unique private PersonalDimensionRegistry universalTool$registry;
    @Unique private boolean universalTool$tickingLevels;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void universalTool$initializeDimensions(Thread thread, LevelStorageSource.LevelStorageAccess storage,
                                                    PackRepository packs, WorldStem stem, Proxy proxy,
                                                    com.mojang.datafixers.DataFixer dataFixer, Services services,
                                                    ChunkProgressListenerFactory listenerFactory, CallbackInfo ci) {
        universalTool$registry = new PersonalDimensionRegistry((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickChildren", at = @At("HEAD"))
    private void universalTool$registerPendingLevels(BooleanSupplier keepTicking, CallbackInfo ci) {
        for (ServerLevel level : List.copyOf(universalTool$pendingLevels)) universalTool$registerLevel(level);
        universalTool$pendingLevels.clear();
        for (ResourceKey<Level> key : List.copyOf(universalTool$pendingUnloads)) universalTool$unloadNow(key);
        universalTool$pendingUnloads.clear();
        universalTool$tickingLevels = true;
    }

    @Inject(method = "tickChildren", at = @At("RETURN"))
    private void universalTool$finishLevelTick(BooleanSupplier keepTicking, CallbackInfo ci) {
        universalTool$tickingLevels = false;
    }

    @Override
    public PersonalDimensionRegistry universalTool$dimensionRegistry() {
        return universalTool$registry;
    }

    @Override
    public List<ResourceKey<Level>> universalTool$dynamicDimensions() {
        return ((PersonalLevelDataAccess) ((MinecraftServer) (Object) this).getWorldData())
                .universalTool$dynamicDimensions();
    }

    @Override
    public void universalTool$registerLevel(ServerLevel level) {
        if (universalTool$tickingLevels) universalTool$pendingLevels.add(level);
        else universalTool$registerNow(level);
    }

    @Unique
    private void universalTool$registerNow(ServerLevel level) {
        levels.put(level.dimension(), level);
        ((MinecraftServer) (Object) this).markWorldsDirty();
        if (!universalTool$dynamicDimensions().contains(level.dimension())) universalTool$dynamicDimensions().add(level.dimension());
        level.tick(() -> true);
    }

    @Override
    public void universalTool$unloadLevel(ResourceKey<Level> key) {
        if (universalTool$tickingLevels) universalTool$pendingUnloads.add(key);
        else universalTool$unloadNow(key);
    }

    @Unique
    private void universalTool$unloadNow(ResourceKey<Level> key) {
        ServerLevel level = levels.get(key);
        if (level == null) return;
        ResourceLocation typeId = level.dimensionTypeRegistration().unwrapKey().map(ResourceKey::location).orElse(null);
        try (level) {
            level.save(null, true, level.noSave);
        } catch (IOException exception) {
            UniversalToolMod.LOGGER.error("Failed to save personal dimension {} while unloading", key, exception);
        } finally {
            levels.remove(key);
            ((MinecraftServer) (Object) this).markWorldsDirty();
        }

        // Keep small registry entries for the session so raw dimension-type IDs never shift.
        // The loaded ServerLevel and chunk caches above are still closed and removed.
        universalTool$registry.invalidateClientDimension(key.location());
        for (ServerPlayer player : getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new PersonalDimensionSyncPayload(key.location(), new net.minecraft.nbt.CompoundTag(), true));
        }
    }

    @Override @Accessor("storageSource") public abstract LevelStorageSource.LevelStorageAccess universalTool$getStorageSource();
    @Override @Accessor("executor") public abstract java.util.concurrent.Executor universalTool$getExecutor();
    @Override @Accessor("progressListenerFactory") public abstract net.minecraft.server.level.progress.ChunkProgressListenerFactory universalTool$getProgressListenerFactory();
    @Override @Accessor("resources") public abstract MinecraftServer.ReloadableResources universalTool$getResources();
}
