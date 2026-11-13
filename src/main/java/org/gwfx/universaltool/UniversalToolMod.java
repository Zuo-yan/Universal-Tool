package org.gwfx.universaltool;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.gwfx.universaltool.config.ModConfigs;
import org.gwfx.universaltool.init.ModAttachments;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModBlocks;
import org.gwfx.universaltool.init.ModCreativeTabs;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModMenuTypes;
import org.gwfx.universaltool.init.ModRecipes;
import org.gwfx.universaltool.init.ModSounds;
import org.gwfx.universaltool.init.ModWorldGeneration;
import org.gwfx.universaltool.network.ModPacketHandler;
import org.slf4j.Logger;

@Mod(UniversalToolMod.MODID)
public class UniversalToolMod {
    public static final String MODID = "universal_tool";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UniversalToolMod(IEventBus modEventBus, ModContainer modContainer) {
        ModConfigs.register(modContainer);
        ModDataComponents.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModCreativeTabs::buildContents);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModWorldGeneration.GENERATORS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        if (Boolean.getBoolean("universal_tool.islandTests")) {
            modEventBus.addListener((RegisterGameTestsEvent event) -> {
                event.register(org.gwfx.universaltool.space.PrivateSpaceGameTests.class);
                event.register(org.gwfx.universaltool.skill.SkillProgressionGameTests.class);
                event.register(org.gwfx.universaltool.klein.KleinGameTests.class);
                event.register(org.gwfx.universaltool.repair.UniversalRepairGameTests.class);
                event.register(org.gwfx.universaltool.lottery.LotteryValuatorGameTests.class);
            });
        }
        ModAttachments.register(modEventBus);

        // 注册模组网络数据包处理器 (RegisterPayloadHandlersEvent)
        modEventBus.addListener(ModPacketHandler::register);
    }
}
