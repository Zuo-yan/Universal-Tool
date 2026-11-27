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

    public static final DeferredHolder<SoundEvent, SoundEvent> PHAINON_NORMAL_SKILL_VOICE =
            SOUNDS.register("skill.phainon_normal_skill_voice", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "skill.phainon_normal_skill_voice")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PHAINON_ULTIMATE_ACTIVATE =
            SOUNDS.register("skill.phainon_ultimate_activate", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill.phainon_ultimate_activate")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PHAINON_ULTIMATE_RELEASE =
            SOUNDS.register("skill.phainon_ultimate_release", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill.phainon_ultimate_release")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PHAINON_METEOR_VOICE =
            SOUNDS.register("skill.phainon_meteor_voice", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill.phainon_meteor_voice")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PHAINON_COUNTER_GUARD_VOICE =
            SOUNDS.register("skill.phainon_counter_guard_voice", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill.phainon_counter_guard_voice")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PHAINON_COUNTER_STRIKE_VOICE =
            SOUNDS.register("skill.phainon_counter_strike_voice", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill.phainon_counter_strike_voice")));

    public static final DeferredHolder<SoundEvent, SoundEvent> NAIWA_LAUGH =
            SOUNDS.register("entity.naiwa.laugh", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "entity.naiwa.laugh")));

    // 海湾 GT 跑车音效
    public static final DeferredHolder<SoundEvent, SoundEvent> SPORTS_CAR_ENGINE =
            SOUNDS.register("sports_car_engine", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "sports_car_engine")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPORTS_CAR_DRIFT =
            SOUNDS.register("sports_car_drift", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "sports_car_drift")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPORTS_CAR_NITRO =
            SOUNDS.register("sports_car_nitro", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "sports_car_nitro")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPORTS_CAR_DOOR =
            SOUNDS.register("sports_car_door", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "sports_car_door")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPORTS_CAR_MORPH =
            SOUNDS.register("sports_car_morph", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "sports_car_morph")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPORTS_CAR_WATER_GLIDE =
            SOUNDS.register("sports_car_water_glide", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                            "sports_car_water_glide")));

    private ModSounds() {
    }
}
