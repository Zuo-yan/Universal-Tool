package org.gwfx.universaltool.space;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import com.mojang.serialization.Lifecycle;

import java.util.Map;

public interface PersonalMappedRegistryAccess<T> {
    Map<ResourceLocation, Holder.Reference<T>> universalTool$byLocation();
    Map<ResourceKey<T>, Holder.Reference<T>> universalTool$byKey();
    Map<T, Holder.Reference<T>> universalTool$byValue();
    Map<ResourceKey<T>, RegistrationInfo> universalTool$registrationInfos();
    ObjectList<Holder.Reference<T>> universalTool$byId();
    Reference2IntMap<T> universalTool$toId();
    Map<T, Holder.Reference<T>> universalTool$intrusiveHolders();
    Map<TagKey<T>, HolderSet.Named<T>> universalTool$tags();
    boolean universalTool$isFrozen();
    void universalTool$setFrozen(boolean frozen);
    void universalTool$setLifecycle(Lifecycle lifecycle);
}
