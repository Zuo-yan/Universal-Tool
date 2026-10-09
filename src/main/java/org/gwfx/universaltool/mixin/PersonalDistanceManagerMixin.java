package org.gwfx.universaltool.mixin;

import net.minecraft.server.level.DistanceManager;
import org.gwfx.universaltool.space.PersonalDistanceManagerAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DistanceManager.class)
public abstract class PersonalDistanceManagerMixin implements PersonalDistanceManagerAccess {
    @Override @Accessor("simulationDistance") public abstract int universalTool$getSimulationDistance();
}
