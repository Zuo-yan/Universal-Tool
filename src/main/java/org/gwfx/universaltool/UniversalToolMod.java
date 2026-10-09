package org.gwfx.universaltool;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.gwfx.universaltool.client.PhoenixPodRenderer;
import org.gwfx.universaltool.client.PhoenixPodScreen;
import org.gwfx.universaltool.client.PolymerizerScreen;
import org.gwfx.universaltool.init.*;
import org.gwfx.universaltool.network.ModPacketHandler;
import org.gwfx.universaltool.waypoint.WaypointData;

@Mod(UniversalToolMod.MODID)
public class UniversalToolMod {
    public static final String MODID = "universal_tool";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UniversalToolMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModWorldGeneration.GENERATORS.register(modEventBus);
        if (Boolean.getBoolean("universal_tool.islandTests")) {
            modEventBus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) ->
                    event.register(org.gwfx.universaltool.space.PrivateSpaceGameTests.class));
        }
        WaypointData.ATTACHMENTS.register(modEventBus);

        // 注册模组网络数据包处理器 (RegisterPayloadHandlersEvent)
        modEventBus.addListener(ModPacketHandler::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerScreens);
            modEventBus.addListener(this::registerRenderers);
        }
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.UNIVERSAL_POLYMERIZER_MENU.get(), PolymerizerScreen::new);
        event.register(ModMenuTypes.PHOENIX_POD_MENU.get(), PhoenixPodScreen::new);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PHOENIX_POD_BE.get(), PhoenixPodRenderer::new);
    }
}
