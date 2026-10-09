package org.gwfx.universaltool.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.block.UniversalPolymerizerBlockEntity;
import org.gwfx.universaltool.phoenix.PhoenixPodBlockEntity;
import org.gwfx.universaltool.space.PersonalSpaceGateBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UniversalToolMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UniversalPolymerizerBlockEntity>> UNIVERSAL_POLYMERIZER_BE =
            BLOCK_ENTITIES.register("universal_polymerizer", () ->
                    BlockEntityType.Builder.of(UniversalPolymerizerBlockEntity::new, ModBlocks.UNIVERSAL_POLYMERIZER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhoenixPodBlockEntity>> PHOENIX_POD_BE =
            BLOCK_ENTITIES.register("operation_phoenix_pod", () ->
                    BlockEntityType.Builder.of(PhoenixPodBlockEntity::new, ModBlocks.OPERATION_PHOENIX_POD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PersonalSpaceGateBlockEntity>> PERSONAL_SPACE_GATE_BE =
            BLOCK_ENTITIES.register("personal_space_gate", () ->
                    BlockEntityType.Builder.of(PersonalSpaceGateBlockEntity::new, ModBlocks.PERSONAL_SPACE_GATE.get()).build(null));
}
