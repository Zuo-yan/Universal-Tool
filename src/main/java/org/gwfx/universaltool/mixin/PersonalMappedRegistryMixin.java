package org.gwfx.universaltool.mixin;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.MappedRegistry;
import net.minecraft.tags.TagKey;
import org.gwfx.universaltool.space.PersonalMappedRegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;
import java.util.Map;

@Mixin(MappedRegistry.class)
public interface PersonalMappedRegistryMixin<T> extends PersonalMappedRegistryAccess<T> {
    @Override @Accessor("byLocation") Map<ResourceLocation, Holder.Reference<T>> universalTool$byLocation();
    @Override @Accessor("byKey") Map<ResourceKey<T>, Holder.Reference<T>> universalTool$byKey();
    @Override @Accessor("byValue") Map<T, Holder.Reference<T>> universalTool$byValue();
    @Override @Accessor("registrationInfos") Map<ResourceKey<T>, RegistrationInfo> universalTool$registrationInfos();
    @Override @Accessor("byId") ObjectList<Holder.Reference<T>> universalTool$byId();
    @Override @Accessor("toId") Reference2IntMap<T> universalTool$toId();
    @Override @Nullable @Accessor("unregisteredIntrusiveHolders") Map<T, Holder.Reference<T>> universalTool$intrusiveHolders();
    @Override @Accessor("tags") Map<TagKey<T>, HolderSet.Named<T>> universalTool$tags();
    @Override @Accessor("frozen") boolean universalTool$isFrozen();
    @Override @Accessor("frozen") void universalTool$setFrozen(boolean frozen);
    @Override @Accessor("registryLifecycle") void universalTool$setLifecycle(Lifecycle lifecycle);
}
