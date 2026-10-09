package org.gwfx.universaltool.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UniversalToolMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> UNIVERSAL_TOOL_TAB =
            CREATIVE_MODE_TABS.register("universal_tool_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.universal_tool"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.DIAMOND_UNIVERSAL_TOOL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 6 阶万能工具
                        output.accept(ModItems.WOODEN_UNIVERSAL_TOOL.get());
                        output.accept(ModItems.STONE_UNIVERSAL_TOOL.get());
                        output.accept(ModItems.IRON_UNIVERSAL_TOOL.get());
                        output.accept(ModItems.GOLDEN_UNIVERSAL_TOOL.get());
                        output.accept(ModItems.DIAMOND_UNIVERSAL_TOOL.get());
                        output.accept(ModItems.NETHERITE_UNIVERSAL_TOOL.get());
                        // 空间传送
                        output.accept(ModItems.TELEPORT_CODEX.get());
                        output.accept(ModItems.SPATIAL_PAGE.get());
                        output.accept(ModItems.TELEPORT_SCROLL.get());
                        // 核心工作台
                        output.accept(ModItems.UNIVERSAL_POLYMERIZER.get());
                        // 凤凰计划
                        output.accept(ModItems.DNA_SYRINGE.get());
                        output.accept(ModItems.NUTRIENT_SOLUTION.get());
                        output.accept(ModItems.OPERATION_PHOENIX_POD.get());
                        output.accept(ModItems.PERSONAL_SPACE_GATE.get());
                    })
                    .build());
}
