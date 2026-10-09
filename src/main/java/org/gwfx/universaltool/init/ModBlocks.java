package org.gwfx.universaltool.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.block.UniversalPolymerizerBlock;
import org.gwfx.universaltool.space.PersonalSpaceGateBlock;
import org.gwfx.universaltool.space.PersonalSpaceReturnBlock;
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

    public static final DeferredBlock<PersonalSpaceGateBlock> PERSONAL_SPACE_GATE = BLOCKS.register("personal_space_gate",
            () -> new PersonalSpaceGateBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 10.0F)
                    .noOcclusion()
                    .lightLevel(state -> 5)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)));

    public static final DeferredBlock<PersonalSpaceReturnBlock> PERSONAL_SPACE_RETURN = BLOCKS.register("personal_space_return",
            () -> new PersonalSpaceReturnBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3_600_000.0F)
                    .lightLevel(state -> 7)
                    .noLootTable()
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.AMETHYST)));
}
