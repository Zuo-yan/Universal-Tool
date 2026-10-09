package org.gwfx.universaltool.space;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import com.mojang.serialization.Lifecycle;
import com.google.common.collect.ImmutableList;

public final class PersonalRegistryUtil {
    private PersonalRegistryUtil() {}

    public static <T> Holder.Reference<T> register(Registry<T> registry, ResourceLocation id, T value) {
        if (registry.containsKey(id)) throw new IllegalStateException("Dimension registry ID already exists: " + id);
        if (!(registry instanceof MappedRegistry<T> mapped)) {
            throw new IllegalStateException("Unsupported registry implementation: " + registry.getClass().getName());
        }
        PersonalMappedRegistryAccess<T> access = (PersonalMappedRegistryAccess<T>) mapped;
        boolean frozen = access.universalTool$isFrozen();
        if (frozen) access.universalTool$setFrozen(false);
        try {
            return mapped.register(ResourceKey.create(registry.key(), id), value, RegistrationInfo.BUILT_IN);
        } finally {
            if (frozen) registry.freeze();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> void unregister(Registry<T> registry, ResourceLocation id) {
        if (!registry.containsKey(id) || !(registry instanceof MappedRegistry<T> mapped)) return;
        PersonalMappedRegistryAccess<T> access = (PersonalMappedRegistryAccess<T>) mapped;
        ResourceKey<T> key = ResourceKey.create(registry.key(), id);
        T value = access.universalTool$byLocation().get(id).value();
        int rawId = access.universalTool$toId().removeInt(value);
        access.universalTool$byId().remove(rawId);
        access.universalTool$toId().replaceAll((entry, index) -> index > rawId ? index - 1 : index);
        access.universalTool$byLocation().remove(id);
        access.universalTool$byKey().remove(key);
        access.universalTool$byValue().remove(value);
        access.universalTool$registrationInfos().remove(key);
        if (access.universalTool$intrusiveHolders() != null) access.universalTool$intrusiveHolders().remove(value);
        for (HolderSet.Named<T> holderSet : access.universalTool$tags().values()) {
            PersonalHolderSetNamedAccess<T> named = (PersonalHolderSetNamedAccess<T>) holderSet;
            ImmutableList.Builder<net.minecraft.core.Holder<T>> contents = ImmutableList.builder();
            for (net.minecraft.core.Holder<T> holder : named.universalTool$getContents()) {
                if (!holder.is(id)) contents.add(holder);
            }
            named.universalTool$setContents(contents.build());
        }
        Lifecycle lifecycle = Lifecycle.stable();
        for (RegistrationInfo info : access.universalTool$registrationInfos().values()) lifecycle = lifecycle.add(info.lifecycle());
        access.universalTool$setLifecycle(lifecycle);
    }
}
