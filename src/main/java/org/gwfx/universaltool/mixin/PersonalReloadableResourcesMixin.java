package org.gwfx.universaltool.mixin;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.tags.TagManager;
import org.gwfx.universaltool.space.PersonalReloadableResourcesAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ReloadableServerResources.class)
public interface PersonalReloadableResourcesMixin extends PersonalReloadableResourcesAccess {
    @Override @Accessor("tagManager") TagManager universalTool$getTagManager();
}
