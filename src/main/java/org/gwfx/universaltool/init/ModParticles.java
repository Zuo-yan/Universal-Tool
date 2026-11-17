package org.gwfx.universaltool.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Custom particle type registrations for Arcane-Cosmic wormhole and spatial effects.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, UniversalToolMod.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WORMHOLE_SPARK =
            PARTICLE_TYPES.register("wormhole_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WORMHOLE_EXIT_SPARK =
            PARTICLE_TYPES.register("wormhole_exit_spark", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
