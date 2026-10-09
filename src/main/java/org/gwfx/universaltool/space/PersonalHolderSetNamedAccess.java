package org.gwfx.universaltool.space;

import net.minecraft.core.Holder;

import java.util.List;

public interface PersonalHolderSetNamedAccess<T> {
    List<Holder<T>> universalTool$getContents();
    void universalTool$setContents(List<Holder<T>> contents);
}
