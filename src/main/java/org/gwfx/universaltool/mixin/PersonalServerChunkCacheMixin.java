package org.gwfx.universaltool.mixin;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import org.gwfx.universaltool.space.PersonalServerChunkCacheAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkCache.class)
public abstract class PersonalServerChunkCacheMixin implements PersonalServerChunkCacheAccess {
    @Override @Accessor("distanceManager") public abstract DistanceManager universalTool$getDistanceManager();
}
