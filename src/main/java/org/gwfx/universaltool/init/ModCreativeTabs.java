package org.gwfx.universaltool.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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
                        output.accept(ModItems.QINCHEN_SWORD.get());
                        // 空间传送
                        output.accept(ModItems.TELEPORT_CODEX.get());
                        output.accept(ModItems.SPATIAL_PAGE.get());
                        output.accept(ModItems.TELEPORT_SCROLL.get());
                        output.accept(ModItems.KLEIN_BOTTLE.get());
                        // 核心工作台与抽奖
                        output.accept(ModItems.UNIVERSAL_POLYMERIZER.get());
                        output.accept(ModItems.UNIVERSAL_REPAIR_MATERIAL.get());
                        output.accept(ModItems.UNIVERSAL_LOTTERY_MACHINE.get());
                        output.accept(ModItems.VOID_SCRAP.get());
                        output.accept(ModItems.VOID_ESSENCE.get());
                        // 凤凰计划
                        output.accept(ModItems.DNA_SYRINGE.get());
                        output.accept(ModItems.NUTRIENT_SOLUTION.get());
                        output.accept(ModItems.OPERATION_PHOENIX_POD.get());
                        output.accept(ModItems.PERSONAL_SPACE_GATE.get());
                        // 全地形载具与跑车
                        output.accept(ModItems.ALL_TERRAIN_WHEELCHAIR.get());
                        output.accept(ModItems.GULF_GT_CAR.get());
                        // 奶蛙与 MCphone
                        output.accept(ModItems.NAIWA_SPAWN_EGG.get());
                        output.accept(com.november.mcphone.core.ModItems.PHONE.get());
                        output.accept(com.november.mcphone.core.ModItems.TABLET.get());
                    })
                    .build());

    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.QINCHEN_SWORD.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.UNIVERSAL_REPAIR_MATERIAL.get());
            event.accept(ModItems.VOID_ESSENCE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.ALL_TERRAIN_WHEELCHAIR.get());
            event.accept(ModItems.GULF_GT_CAR.get());
        }
    }
}
