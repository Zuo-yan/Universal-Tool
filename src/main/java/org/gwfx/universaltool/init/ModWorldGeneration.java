package org.gwfx.universaltool.init;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.space.FiniteIslandChunkGenerator;

public final class ModWorldGeneration {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UniversalToolMod.MODID);
    static { GENERATORS.register("finite_island", () -> FiniteIslandChunkGenerator.CODEC); }
}
