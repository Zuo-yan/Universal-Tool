package org.gwfx.universaltool.space;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;

public interface PersonalDimensionServerAccess {
    PersonalDimensionRegistry universalTool$dimensionRegistry();
    List<ResourceKey<Level>> universalTool$dynamicDimensions();
    void universalTool$registerLevel(ServerLevel level);
    void universalTool$unloadLevel(ResourceKey<Level> key);
}
