package org.gwfx.universaltool.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.gwfx.universaltool.space.PersonalLevelDataAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

@Mixin(PrimaryLevelData.class)
public abstract class PersonalLevelDataMixin implements PersonalLevelDataAccess {
    @Unique private final List<ResourceKey<Level>> universalTool$dynamicDimensions = new ArrayList<>();

    @Inject(method = "setTagData", at = @At("RETURN"))
    private void universalTool$excludeRuntimeDimensions(RegistryAccess registries, CompoundTag levelTag,
                                                       CompoundTag playerTag, CallbackInfo callback) {
        CompoundTag dimensions = levelTag.getCompound("WorldGenSettings").getCompound("dimensions");
        for (ResourceKey<Level> key : universalTool$dynamicDimensions) dimensions.remove(key.location().toString());
    }

    @Override
    public List<ResourceKey<Level>> universalTool$dynamicDimensions() {
        return universalTool$dynamicDimensions;
    }
}
