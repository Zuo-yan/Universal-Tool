package org.gwfx.universaltool.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import org.gwfx.universaltool.space.PersonalHolderSetNamedAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(HolderSet.Named.class)
public interface PersonalHolderSetNamedMixin<T> extends PersonalHolderSetNamedAccess<T> {
    @Override @Accessor("contents") List<Holder<T>> universalTool$getContents();
    @Override @Accessor("contents") void universalTool$setContents(List<Holder<T>> contents);
}
