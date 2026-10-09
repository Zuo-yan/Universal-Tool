package org.gwfx.universaltool.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.block.UniversalPolymerizerBlock;
import org.gwfx.universaltool.phoenix.PhoenixPodBlock;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UniversalToolMod.MODID);

    public static final DeferredBlock<UniversalPolymerizerBlock> UNIVERSAL_POLYMERIZER = BLOCKS.register("universal_polymerizer",
            () -> new UniversalPolymerizerBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F, 3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    public static final DeferredBlock<PhoenixPodBlock> OPERATION_PHOENIX_POD = BLOCKS.register("operation_phoenix_pod",
            () -> new PhoenixPodBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.GLASS)));
}
