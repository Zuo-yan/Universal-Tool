package org.gwfx.universaltool.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, UniversalToolMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHTNING_SPEAR_THROW =
            SOUNDS.register("skill.lightning_spear_throw", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "skill.lightning_spear_throw")));

    private ModSounds() {
    }
}
