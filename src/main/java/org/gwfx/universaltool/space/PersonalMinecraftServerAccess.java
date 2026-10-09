package org.gwfx.universaltool.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.concurrent.Executor;

public interface PersonalMinecraftServerAccess {
    LevelStorageSource.LevelStorageAccess universalTool$getStorageSource();
    Executor universalTool$getExecutor();
    ChunkProgressListenerFactory universalTool$getProgressListenerFactory();
    MinecraftServer.ReloadableResources universalTool$getResources();
}
