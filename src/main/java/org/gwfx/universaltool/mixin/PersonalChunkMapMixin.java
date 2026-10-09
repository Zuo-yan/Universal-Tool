package org.gwfx.universaltool.mixin;

import net.minecraft.server.level.ChunkMap;
import org.gwfx.universaltool.space.PersonalChunkMapAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public abstract class PersonalChunkMapMixin implements PersonalChunkMapAccess {
    @Override @Accessor("serverViewDistance") public abstract int universalTool$getViewDistance();
}
